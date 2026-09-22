
@RestController
@RequestMapping("api/v1/orders")

public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

   @PostMapping
   public ResponseEntity<OrderResponse> placeOrder(@RequestBody OrderRequest orderRequest) {
       OrderResponse orderResponse = orderService.placeOrder(orderRequest);
       return ResponseEntity.ok(orderResponse);
   }


   @DeleteMapping("/{orderId}")
   public ResponseEntity<OrderResponse> cancelOrder(@PathVariable String orderId) {
       OrderResponse orderResponse = orderService.cancelOrder(orderId);
       return ResponseEntity.ok(orderResponse);
   }
}