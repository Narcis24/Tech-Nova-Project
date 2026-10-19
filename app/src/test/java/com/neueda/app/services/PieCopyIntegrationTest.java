package com.neueda.app.services;

import com.neueda.app.dtos.AccountPieResponse;
import com.neueda.app.dtos.PieResponse;
import com.neueda.app.dtos.PieSliceRequest;
import com.neueda.app.dtos.PlaceOrderRequest;
import com.neueda.app.dtos.RebalanceLegResponse;
import com.neueda.app.dtos.RebalanceRunResponse;
import com.neueda.app.dtos.SavePieRequest;
import com.neueda.app.enums.AccountStatus;
import com.neueda.app.enums.AssetClass;
import com.neueda.app.enums.OrderStatus;
import com.neueda.app.enums.PieLinkMode;
import com.neueda.app.enums.PieVisibility;
import com.neueda.app.enums.RebalanceStatus;
import com.neueda.app.exceptions.AccountNotActiveException;
import com.neueda.app.exceptions.PieNotFoundException;
import com.neueda.app.exceptions.RebalanceConflictException;
import com.neueda.app.models.Account;
import com.neueda.app.models.AccountPie;
import com.neueda.app.models.Instrument;
import com.neueda.app.models.Order;
import com.neueda.app.models.Pie;
import com.neueda.app.models.Price;
import com.neueda.app.models.RebalanceRun;
import com.neueda.app.repositories.AccountPieRepository;
import com.neueda.app.repositories.AccountRepository;
import com.neueda.app.repositories.InstrumentRepository;
import com.neueda.app.repositories.OrderRepository;
import com.neueda.app.repositories.PieRepository;
import com.neueda.app.repositories.PieSliceRepository;
import com.neueda.app.repositories.PieVersionRepository;
import com.neueda.app.repositories.PositionRepository;
import com.neueda.app.repositories.PriceRepository;
import com.neueda.app.repositories.RebalanceLegRepository;
import com.neueda.app.repositories.RebalanceRunRepository;
import com.neueda.events.EventEnvelope;
import com.neueda.events.OrderExecutedEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/** Copy previews and explicitly confirmed runs against a real database, including settlement. */
@DataJpaTest
@Import({AccountPieService.class, RebalanceRunService.class,
    RebalanceCoordinator.class, RebalancePlanner.class, RebalanceCalculator.class, PortfolioService.class,
    PositionValuator.class, PriceService.class, OrderService.class, OrderValidator.class, OrderSettlementService.class,
    PieService.class, PieCopyIntegrationTest.JsonConfig.class})
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PieCopyIntegrationTest {

    private static final String AUTHOR = "author";
    private static final String BUYER = "buyer";
    private static final String ACC1 = "COPY001";
    private static final Instant START = Instant.parse("2026-10-14T15:00:00Z");

    @TestConfiguration
    static class JsonConfig {
        @Bean
        ObjectMapper objectMapper() {
            return JsonMapper.builder().build();
        }
    }

    @Autowired private AccountPieService accountPieService;
    @Autowired private RebalanceRunService runService;
    @Autowired private OrderSettlementService settlement;
    @Autowired private OrderService orderService;
    @Autowired private PieService pieService;
    @Autowired private ObjectMapper mapper;
    @Autowired private AccountRepository accountRepository;
    @Autowired private InstrumentRepository instrumentRepository;
    @Autowired private PriceRepository priceRepository;
    @Autowired private PositionRepository positionRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private PieRepository pieRepository;
    @Autowired private PieVersionRepository pieVersionRepository;
    @Autowired private PieSliceRepository pieSliceRepository;
    @Autowired private AccountPieRepository accountPieRepository;
    @Autowired private RebalanceRunRepository runRepository;
    @Autowired private RebalanceLegRepository legRepository;

    @Autowired private PlatformTransactionManager transactions;

    @MockitoBean private EventProducerService eventProducerService;
    @MockitoBean private RiskService riskService;
    @MockitoBean private Clock clock;

    private UUID pieId;

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(START);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        for (String symbol : List.of("AAPL", "MSFT", "GOOG")) {
            instrumentRepository.save(new Instrument(symbol, symbol + " Inc.", AssetClass.EQUITY, "USD", true));
            priceRepository.save(new Price(symbol, LocalDate.now(), new BigDecimal("100.00")));
        }
        pieId = pieService.create(AUTHOR, request("50", "40")).getPieId();
        pieService.publish(pieId, AUTHOR);
    }

    @AfterEach
    void tearDown() {
        legRepository.deleteAll();
        runRepository.deleteAll();
        orderRepository.deleteAll();
        positionRepository.deleteAll();
        accountPieRepository.deleteAll();
        pieSliceRepository.deleteAll();
        pieVersionRepository.deleteAll();
        pieRepository.deleteAll();
        accountRepository.deleteAll();
        priceRepository.deleteAll();
        instrumentRepository.deleteAll();
    }

    // ---------- copying ----------

    @Test
    void copy_linksTheAccountAndStartsTheConfirmedRun() {
        account(ACC1, "10000.00");

        RebalanceRunResponse run = accountPieService.copy(ACC1, BUYER, pieId);

        AccountPie link = link(ACC1);
        assertEquals(PieLinkMode.COPY, link.getMode());
        assertEquals(pieId, link.getPieId());
        assertEquals(1, run.getTargetVersion());
        assertEquals(RebalanceStatus.BUYING, run.getStatus());
        assertEquals(2, orderRepository.count());
        assertEquals(1L, accountPieRepository.countFollowers(List.of(pieId)).get(0).getFollowers());
    }

    @Test
    void previewCopy_linksNothing_andPlacesNothing() {
        account(ACC1, "10000.00");

        var preview = accountPieService.previewCopy(ACC1, BUYER, pieId);

        assertEquals(1, preview.getVersion());
        assertEquals(2, preview.getLegs().stream().filter(leg -> leg.getQuantity() > 0).count());
        assertTrue(accountPieRepository.findById(ACC1).isEmpty());
        assertEquals(0, orderRepository.count());
        assertEquals(0, runRepository.count());
    }

    @Test
    void copy_isAtomic_aRunThatCannotStartLeavesNoLink() {
        account(ACC1, "10000.00");
        accountPieRepository.save(AccountPie.own(ACC1, ownPie(BUYER)));
        orderService.placeOrder(new PlaceOrderRequest(ACC1, "AAPL", "BUY", "MARKET", 1, null, "manual-1"));

        assertThrows(RebalanceConflictException.class, () -> accountPieService.copy(ACC1, BUYER, pieId));

        assertEquals(PieLinkMode.OWN, link(ACC1).getMode());
        assertEquals(0, runRepository.count());
        assertEquals(1, orderRepository.count());
    }

    @Test
    void copy_withARunActive_conflictAndNothingChanges() {
        account(ACC1, "10000.00");
        UUID mine = ownPie(BUYER);
        accountPieRepository.save(AccountPie.own(ACC1, mine));
        runService.start(ACC1);

        assertThrows(RebalanceConflictException.class, () -> accountPieService.copy(ACC1, BUYER, pieId));

        assertEquals(mine, link(ACC1).getPieId());
        assertEquals(1, runRepository.count());
    }

    @Test
    void copy_yourOwnPie_isRefusedWhetherPublicOrPrivate() {
        account(ACC1, "10000.00");
        UUID privatePie = ownPie(AUTHOR);

        assertThrows(RebalanceConflictException.class, () -> accountPieService.copy(ACC1, AUTHOR, pieId));
        assertThrows(RebalanceConflictException.class, () -> accountPieService.previewCopy(ACC1, AUTHOR, pieId));
        assertThrows(RebalanceConflictException.class, () -> accountPieService.copy(ACC1, AUTHOR, privatePie));
        assertTrue(accountPieRepository.findById(ACC1).isEmpty());
        assertEquals(0, runRepository.count());
    }

    @Test
    void copy_aPrivateOrDeletedPie_isNotFound() {
        account(ACC1, "10000.00");
        UUID privatePie = ownPie(AUTHOR);
        pieService.unpublish(pieId, AUTHOR);

        assertThrows(PieNotFoundException.class, () -> accountPieService.copy(ACC1, BUYER, privatePie));
        assertThrows(PieNotFoundException.class, () -> accountPieService.previewCopy(ACC1, BUYER, pieId));
        pieService.publish(pieId, AUTHOR);
        pieService.delete(pieId, AUTHOR);
        assertThrows(PieNotFoundException.class, () -> accountPieService.copy(ACC1, BUYER, pieId));
        assertTrue(accountPieRepository.findById(ACC1).isEmpty());
        assertEquals(0, runRepository.count());
    }

    @Test
    void copy_intoAnInactiveAccount_isRefused() {
        Account account = account(ACC1, "10000.00");
        setStatus(account, AccountStatus.SUSPENDED);

        assertThrows(AccountNotActiveException.class, () -> accountPieService.copy(ACC1, BUYER, pieId));
        assertThrows(AccountNotActiveException.class, () -> accountPieService.previewCopy(ACC1, BUYER, pieId));
        assertTrue(accountPieRepository.findById(ACC1).isEmpty());
        assertEquals(0, runRepository.count());
    }

    @Test
    void copy_thePieAlreadyCopied_conflict() throws Exception {
        account(ACC1, "10000.00");
        complete(accountPieService.copy(ACC1, BUYER, pieId));

        assertThrows(RebalanceConflictException.class, () -> accountPieService.copy(ACC1, BUYER, pieId));
        assertEquals(1, link(ACC1).getAppliedVersion());
    }

    @Test
    void copiers_cannotSeeOrChangeTheAuthorsPie() throws Exception {
        account(ACC1, "10000.00");
        complete(accountPieService.copy(ACC1, BUYER, pieId));

        assertThrows(PieNotFoundException.class, () -> pieService.get(pieId, BUYER));
        assertThrows(PieNotFoundException.class, () -> pieService.update(pieId, BUYER, request("10", "10")));
        assertThrows(PieNotFoundException.class, () -> pieService.publish(pieId, BUYER));
        assertThrows(PieNotFoundException.class, () -> pieService.delete(pieId, BUYER));
        assertThrows(PieNotFoundException.class, () -> accountPieService.link(ACC1, BUYER, pieId));
        assertEquals(List.of(), pieService.get(pieId, AUTHOR).getLinkedAccountIds());
    }

    // ---------- explicit updates ----------

    @Test
    void authorEditsOnlyOfferAnUpdateUntilTheCopierConfirms() throws Exception {
        account(ACC1, "10000.00");
        complete(accountPieService.copy(ACC1, BUYER, pieId));
        long orders = orderRepository.count();

        edit("30", "30");
        edit("20", "20");

        AccountPieResponse response = accountPieService.get(ACC1);
        assertTrue(response.isUpdateAvailable());
        assertEquals(1, response.getAppliedVersion());
        assertEquals(3, response.getLatestVersion());
        assertEquals(List.of(1), targetVersions());
        assertEquals(orders, orderRepository.count());
        assertEquals(3, accountPieService.preview(ACC1).getVersion());
        assertEquals(orders, orderRepository.count());

        complete(runService.start(ACC1));

        assertEquals(List.of(1, 3), targetVersions());
        assertEquals(3, link(ACC1).getAppliedVersion());
        assertFalse(accountPieService.get(ACC1).isUpdateAvailable());
    }

    @Test
    void editsDuringARunDoNotStartAnotherRunWhenItFinishes() throws Exception {
        account(ACC1, "10000.00");
        RebalanceRunResponse first = accountPieService.copy(ACC1, BUYER, pieId);
        edit("30", "30");
        edit("20", "20");
        assertThrows(RebalanceConflictException.class, () -> runService.start(ACC1));

        complete(first);

        assertEquals(List.of(1), targetVersions());
        assertEquals(1, link(ACC1).getAppliedVersion());
        assertTrue(accountPieService.get(ACC1).isUpdateAvailable());
        assertEquals(3, accountPieService.preview(ACC1).getVersion());
    }

    @Test
    void cancelAfterAnAuthorEditDoesNotRestartTrading() {
        account(ACC1, "10000.00");
        RebalanceRunResponse first = accountPieService.copy(ACC1, BUYER, pieId);
        edit("30", "30");

        runService.cancel(first.getRunId());
        runService.advance(first.getRunId());

        assertEquals(List.of(1), targetVersions());
        assertEquals(RebalanceStatus.CANCELLED, runOf(1).getStatus());
        assertTrue(accountPieService.get(ACC1).isUpdateAvailable());
        assertTrue(orderRepository.findAll().stream().allMatch(order -> order.getStatus() == OrderStatus.CANCELLED));
    }

    // ---------- the author unpublishes or deletes ----------

    @Test
    void unpublished_theCopyKeepsItsAppliedVersion_andFollowsNothingPrivate() throws Exception {
        account(ACC1, "10000.00");
        complete(accountPieService.copy(ACC1, BUYER, pieId));

        pieService.unpublish(pieId, AUTHOR);
        pieService.update(pieId, AUTHOR, request("10", "10"));  // a private edit: version 2

        assertEquals(List.of(1), targetVersions());
        AccountPieResponse response = accountPieService.get(ACC1);
        assertFalse(response.isPublished());
        assertFalse(response.isUpdateAvailable());
        assertEquals(1, response.getLatestVersion());
        assertEquals(1, accountPieService.preview(ACC1).getVersion());
        assertEquals(1, runService.start(ACC1).getTargetVersion());  // a manual run goes to what it applied
    }

    @Test
    void republished_theCopyOffersTheLatestForManualReview() throws Exception {
        account(ACC1, "10000.00");
        complete(accountPieService.copy(ACC1, BUYER, pieId));
        pieService.unpublish(pieId, AUTHOR);
        pieService.update(pieId, AUTHOR, request("10", "10"));

        pieService.publish(pieId, AUTHOR);

        assertEquals(List.of(1), targetVersions());
        assertTrue(accountPieService.get(ACC1).isUpdateAvailable());
        assertTrue(accountPieService.get(ACC1).isPublished());
    }

    @Test
    void deleted_theCopyKeepsItsAppliedVersion_andNothingIsSold() throws Exception {
        account(ACC1, "10000.00");
        complete(accountPieService.copy(ACC1, BUYER, pieId));
        long orders = orderRepository.count();

        pieService.delete(pieId, AUTHOR);

        AccountPieResponse response = accountPieService.get(ACC1);
        assertFalse(response.isPublished());
        assertFalse(response.isUpdateAvailable());
        assertEquals(1, response.getLatestVersion());
        assertEquals(1, link(ACC1).getAppliedVersion());
        assertEquals(orders, orderRepository.count());
        assertEquals(List.of(1), targetVersions());
    }

    // ---------- stop copying, make it mine ----------

    @Test
    void stopCopying_unlinksAndKeepsTheHoldings_butNotWhileARunIsActive() throws Exception {
        account(ACC1, "10000.00");
        RebalanceRunResponse run = accountPieService.copy(ACC1, BUYER, pieId);

        assertThrows(RebalanceConflictException.class, () -> accountPieService.unlink(ACC1));
        fillAll(run);
        accountPieService.unlink(ACC1);

        assertTrue(accountPieRepository.findById(ACC1).isEmpty());
        assertEquals(2, positionRepository.count());
    }

    @Test
    void makeMine_duplicatesTheAppliedSlicesIntoAPrivateOwnPie_andPlacesNoOrders() throws Exception {
        account(ACC1, "10000.00");
        complete(accountPieService.copy(ACC1, BUYER, pieId));
        pieService.unpublish(pieId, AUTHOR);
        pieService.update(pieId, AUTHOR, request("10", "10"));  // not applied, so not copied
        long orders = orderRepository.count();

        AccountPieResponse response = accountPieService.makeMine(ACC1, BUYER);

        AccountPie link = link(ACC1);
        assertEquals(PieLinkMode.OWN, link.getMode());
        assertEquals(response.getPieId(), link.getPieId());
        assertEquals(1, link.getAppliedVersion());
        Pie mine = pieRepository.findById(link.getPieId()).orElseThrow();
        assertEquals(BUYER, mine.getOwnerUsername());
        assertEquals(PieVisibility.PRIVATE, mine.getVisibility());
        assertEquals(1, mine.getLatestVersion());
        assertEquals("Tech", mine.getName());
        Map<String, BigDecimal> copied = weights(mine.getPieId());
        assertEquals(2, copied.size());
        assertEquals(0, new BigDecimal("50").compareTo(copied.get("AAPL")));
        assertEquals(0, new BigDecimal("40").compareTo(copied.get("MSFT")));
        assertEquals(orders, orderRepository.count());
        assertEquals(1, runRepository.count());
        assertFalse(accountPieService.get(ACC1).isUpdateAvailable());
        assertEquals("PRIVATE", pieService.get(mine.getPieId(), BUYER).getVisibility());
    }

    @Test
    void makeMine_theAuthorsLaterEditsNoLongerReachTheAccount() throws Exception {
        account(ACC1, "10000.00");
        complete(accountPieService.copy(ACC1, BUYER, pieId));
        accountPieService.makeMine(ACC1, BUYER);

        edit("30", "30");

        assertEquals(List.of(1), targetVersions());
        assertTrue(accountPieRepository.countFollowers(List.of(pieId)).isEmpty());
    }

    @Test
    void makeMine_isRefusedWhileARunIsActive_orBeforeAnythingWasApplied() throws Exception {
        account(ACC1, "10000.00");
        RebalanceRunResponse run = accountPieService.copy(ACC1, BUYER, pieId);
        long pies = pieRepository.count();

        assertThrows(RebalanceConflictException.class, () -> accountPieService.makeMine(ACC1, BUYER));

        runService.cancel(run.getRunId());
        assertThrows(RebalanceConflictException.class, () -> accountPieService.makeMine(ACC1, BUYER));
        assertEquals(PieLinkMode.COPY, link(ACC1).getMode());
        assertEquals(pies, pieRepository.count());
    }

    @Test
    void makeMine_needsACopy() {
        account(ACC1, "10000.00");
        accountPieRepository.save(AccountPie.own(ACC1, ownPie(BUYER)));

        assertThrows(RebalanceConflictException.class, () -> accountPieService.makeMine(ACC1, BUYER));
    }

    @Test
    void completedVersionCannotOverwriteALinkToAnotherPie() {
        account(ACC1, "10000.00");
        UUID mine = ownPie(BUYER);
        accountPieRepository.save(AccountPie.own(ACC1, mine));

        new TransactionTemplate(transactions).executeWithoutResult(status ->
            assertEquals(0, accountPieRepository.applyVersion(ACC1, pieId, 1)));

        assertEquals(mine, link(ACC1).getPieId());
        assertEquals(null, link(ACC1).getAppliedVersion());
    }

    // ---------- helpers ----------

    private Account account(String id, String cash) {
        return accountRepository.save(new Account(id, "Copier", new BigDecimal(cash), AccountStatus.ACTIVE, LocalDateTime.now()));
    }

    private void setStatus(Account account, AccountStatus status) {
        accountRepository.save(new Account(account.getAccountId(), "Copier", account.getCashBalance(), status, LocalDateTime.now()));
    }

    private AccountPie link(String accountId) {
        return accountPieRepository.findById(accountId).orElseThrow();
    }

    private UUID ownPie(String owner) {
        return pieService.create(owner, request("20", "20")).getPieId();
    }

    private static SavePieRequest request(String aapl, String msft) {
        return new SavePieRequest("Tech", null, List.of(
            new PieSliceRequest("AAPL", new BigDecimal(aapl)), new PieSliceRequest("MSFT", new BigDecimal(msft))));
    }

    /** The author saves a new version of the public pie. */
    private void edit(String aapl, String msft) {
        pieService.update(pieId, AUTHOR, request(aapl, msft));
    }

    private List<Integer> targetVersions() {
        return runRepository.findAll().stream().map(RebalanceRun::getTargetVersion).sorted().toList();
    }

    private RebalanceRun runOf(int targetVersion) {
        return runRepository.findAll().stream().filter(r -> r.getTargetVersion() == targetVersion).findFirst().orElseThrow();
    }

    private Map<String, BigDecimal> weights(UUID id) {
        return pieSliceRepository.findByPieIdAndVersionOrderByWeightPctDescSymbolAsc(id, 1).stream()
            .collect(Collectors.toMap(s -> s.getSymbol(), s -> s.getWeightPct()));
    }

    /** Fills every order of the run and returns once it has ended. */
    private void complete(RebalanceRunResponse run) throws Exception {
        fillAll(run);
        assertEquals(RebalanceStatus.COMPLETED, runService.get(run.getRunId()).getStatus());
    }

    private void fillAll(RebalanceRunResponse run) throws Exception {
        for (int round = 0; round < 2; round++) {  // the buys exist only once the sells have filled
            for (RebalanceLegResponse leg : runService.get(run.getRunId()).getLegs()) {
                if (leg.getStatus() == OrderStatus.PENDING) {
                    fill(run.getAccountId(), leg.getOrderId());
                }
            }
        }
    }

    /** Delivers ORDER_EXECUTED to settlement, as the Kafka listener would. */
    private void fill(String accountId, UUID orderId) throws Exception {
        Order order = orderRepository.findById(orderId).orElseThrow();
        BigDecimal price = new BigDecimal("100.00");
        OrderExecutedEvent event = new OrderExecutedEvent(orderId, accountId, order.getSymbol(), order.getSide().name(),
            order.getQuantity(), price, price.multiply(BigDecimal.valueOf(order.getQuantity())));
        EventEnvelope<Object> envelope = new EventEnvelope<>(UUID.randomUUID().toString(), "ORDER_EXECUTED",
            Instant.now(), "test", 1, event);
        settlement.processKafkaMessage(mapper.writeValueAsString(envelope));
    }
}
