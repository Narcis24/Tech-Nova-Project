package com.neueda.app.services;

import com.neueda.app.dtos.PieResponse;
import com.neueda.app.dtos.PieSliceRequest;
import com.neueda.app.dtos.PieSliceResponse;
import com.neueda.app.dtos.SavePieRequest;
import com.neueda.app.exceptions.InvalidPieException;
import com.neueda.app.exceptions.PieInUseException;
import com.neueda.app.exceptions.PieNotFoundException;
import com.neueda.app.models.Account;
import com.neueda.app.models.AccountPie;
import com.neueda.app.models.Instrument;
import com.neueda.app.models.Pie;
import com.neueda.app.models.PieSlice;
import com.neueda.app.models.PieVersion;
import com.neueda.app.repositories.AccountPieRepository;
import com.neueda.app.repositories.AccountRepository;
import com.neueda.app.repositories.InstrumentRepository;
import com.neueda.app.repositories.PieRepository;
import com.neueda.app.repositories.PieSliceRepository;
import com.neueda.app.repositories.PieVersionRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Pies and their versioned target weights. A new version is made only when the weights change. */
@Service
public class PieService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final PieRepository pieRepository;
    private final PieVersionRepository pieVersionRepository;
    private final PieSliceRepository pieSliceRepository;
    private final AccountPieRepository accountPieRepository;
    private final AccountRepository accountRepository;
    private final InstrumentRepository instrumentRepository;

    public PieService(PieRepository pieRepository, PieVersionRepository pieVersionRepository,
                      PieSliceRepository pieSliceRepository, AccountPieRepository accountPieRepository,
                      AccountRepository accountRepository, InstrumentRepository instrumentRepository) {
        this.pieRepository = pieRepository;
        this.pieVersionRepository = pieVersionRepository;
        this.pieSliceRepository = pieSliceRepository;
        this.accountPieRepository = accountPieRepository;
        this.accountRepository = accountRepository;
        this.instrumentRepository = instrumentRepository;
    }

    @Transactional(readOnly = true)
    public List<PieResponse> listMine(String owner) {
        return pieRepository.findByOwnerUsernameAndDeletedAtIsNullOrderByCreatedAt(owner).stream()
            .map(pie -> toResponse(pie, latestSlices(pie)))
            .toList();
    }

    @Transactional(readOnly = true)
    public PieResponse get(UUID pieId, String owner) {
        Pie pie = requireOwned(pieId, owner);
        return toResponse(pie, latestSlices(pie));
    }

    @Transactional
    public PieResponse create(String owner, SavePieRequest req) {
        Map<String, BigDecimal> weights = validate(req);
        Pie pie = pieRepository.save(Pie.create(owner, req.getName(), req.getDescription()));
        List<PieSlice> slices = saveVersion(pie.getPieId(), 1, weights);
        return toResponse(pie, slices);
    }

    @Transactional
    public PieResponse update(UUID pieId, String owner, SavePieRequest req) {
        Pie pie = requireOwned(pieId, owner);
        Map<String, BigDecimal> weights = validate(req);
        pie.setName(req.getName());
        pie.setDescription(req.getDescription());

        List<PieSlice> slices = latestSlices(pie);
        if (!normalised(weights).equals(normalised(toWeights(slices)))) {
            pie.setLatestVersion(pie.getLatestVersion() + 1);
            slices = saveVersion(pieId, pie.getLatestVersion(), weights);
        }
        return toResponse(pieRepository.save(pie), slices);
    }

    @Transactional
    public void delete(UUID pieId, String owner) {
        Pie pie = requireOwned(pieId, owner);
        List<String> accountIds = accountRepository.findByOwnerUsernameOrderByAccountId(owner).stream()
            .map(Account::getAccountId)
            .toList();
        if (accountPieRepository.existsByPieIdAndAccountIdIn(pieId, accountIds)) {
            throw new PieInUseException("Pie is followed by one of your accounts: " + pieId);
        }
        pie.setDeletedAt(LocalDateTime.now(ZoneOffset.UTC));
        pieRepository.save(pie);
    }

    /** Symbol to target weight (%) at the pie's latest version. */
    @Transactional(readOnly = true)
    public Map<String, BigDecimal> latestWeights(UUID pieId) {
        Pie pie = pieRepository.findById(pieId)
            .orElseThrow(() -> new PieNotFoundException("Pie not found: " + pieId));
        return toWeights(latestSlices(pie));
    }

    public Pie requireOwned(UUID pieId, String owner) {
        return pieRepository.findByPieIdAndOwnerUsernameAndDeletedAtIsNull(pieId, owner)
            .orElseThrow(() -> new PieNotFoundException("Pie not found: " + pieId));
    }

    /** Checks the request and returns its weights keyed by trimmed, upper-cased symbol. */
    private Map<String, BigDecimal> validate(SavePieRequest req) {
        Map<String, BigDecimal> weights = new LinkedHashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        for (PieSliceRequest slice : req.getSlices()) {
            String symbol = slice.getSymbol().trim().toUpperCase(Locale.ROOT);
            BigDecimal weight = slice.getWeightPct();
            if (weights.put(symbol, weight) != null) {
                throw new InvalidPieException("Duplicate symbol: " + symbol);
            }
            if (weight.signum() <= 0 || weight.compareTo(HUNDRED) > 0 || weight.stripTrailingZeros().scale() > 2) {
                throw new InvalidPieException("Weight for " + symbol + " must be above 0, at most 100, with at most 2 decimals");
            }
            Instrument instrument = instrumentRepository.findBySymbol(symbol)
                .orElseThrow(() -> new InvalidPieException("Unknown symbol: " + symbol));
            if (!instrument.isTradable()) {
                throw new InvalidPieException("Not tradable: " + symbol);
            }
            total = total.add(weight);
        }
        if (total.compareTo(HUNDRED) > 0) {
            throw new InvalidPieException("Weights add up to more than 100");
        }
        return weights;
    }

    private List<PieSlice> saveVersion(UUID pieId, int version, Map<String, BigDecimal> weights) {
        pieVersionRepository.save(new PieVersion(pieId, version, LocalDateTime.now(ZoneOffset.UTC)));
        List<PieSlice> slices = weights.entrySet().stream()
            .map(e -> new PieSlice(pieId, version, e.getKey(), e.getValue()))
            .sorted(Comparator.comparing(PieSlice::getWeightPct).reversed()
                .thenComparing(PieSlice::getSymbol))
            .toList();
        pieSliceRepository.saveAll(slices);
        return slices;
    }

    private List<PieSlice> latestSlices(Pie pie) {
        return pieSliceRepository.findByPieIdAndVersionOrderByWeightPctDescSymbolAsc(pie.getPieId(), pie.getLatestVersion());
    }

    private static Map<String, BigDecimal> toWeights(List<PieSlice> slices) {
        Map<String, BigDecimal> weights = new LinkedHashMap<>();
        slices.forEach(s -> weights.put(s.getSymbol(), s.getWeightPct()));
        return weights;
    }

    /** Drops trailing zeros so 30 and 30.00 compare equal. */
    private static Map<String, BigDecimal> normalised(Map<String, BigDecimal> weights) {
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        weights.forEach((symbol, weight) -> result.put(symbol, weight.stripTrailingZeros()));
        return result;
    }

    private PieResponse toResponse(Pie pie, List<PieSlice> slices) {
        List<PieSliceResponse> sliceResponses = slices.stream()
            .map(s -> new PieSliceResponse(s.getSymbol(), instrumentRepository.findBySymbol(s.getSymbol())
                .map(Instrument::getName).orElse(s.getSymbol()), s.getWeightPct()))
            .toList();
        BigDecimal cashPct = HUNDRED.subtract(slices.stream().map(PieSlice::getWeightPct)
            .reduce(BigDecimal.ZERO, BigDecimal::add));
        List<String> linked = accountPieRepository.findByPieId(pie.getPieId()).stream()
            .map(AccountPie::getAccountId)
            .toList();
        return new PieResponse(pie.getPieId(), pie.getName(), pie.getDescription(), pie.getVisibility().name(),
            pie.getLatestVersion(), sliceResponses, cashPct, linked, pie.getCreatedAt());
    }
}
