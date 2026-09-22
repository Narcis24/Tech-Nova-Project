
@RequestController
@RequestMapping("/accounts")

public class AccountController {


    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<AccountResponse> getAccount(@PathVariable String accountId) {
        AccountResponse accountResponse = accountService.getAccount(accountId);
        return ResponseEntity.ok(accountResponse);
    }

    @GetMapping("/{accountId}/balance") 
    public ResponseEntity<BalanceResponse> getAccountBalance(@PathVariable String accountId) {
        BigDecimal balance = accountService.getAccountBalance(accountId);
        return ResponseEntity.ok(new BalanceResponse(balance));
    }


    @GetMapping("/{accountId}/positions")
    public ResponseEntity<PositionResponse> getAccountPositions(@PathVariable String accountId) {
        PositionResponse positionResponse = accountService.getAccountPositions(accountId);
        return ResponseEntity.ok(positionResponse);
    }

    @GetMapping("/{accountId}/orders")
    public ResponseEntity<OrderResponse> getAccountOrders(@PathVariable String accountId) {
        OrderResponse orderResponse = accountService.getAccountOrders(accountId);
        return ResponseEntity.ok(orderResponse);
    }
}
