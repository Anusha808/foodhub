package com.foodhub.controller;

import com.foodhub.model.CartItem;
import com.foodhub.model.Food;
import com.foodhub.model.Order;
import com.foodhub.model.OrderItem;
import com.foodhub.model.User;

import com.foodhub.repository.CartItemRepository;
import com.foodhub.repository.FoodRepository;
import com.foodhub.repository.OrderRepository;
import com.foodhub.repository.UserRepository;

import com.razorpay.RazorpayClient;
import com.razorpay.Utils;

import jakarta.servlet.http.HttpSession;
import jakarta.transaction.Transactional;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.List;

@Controller
public class CheckoutController {

    private static final Logger logger =
            LoggerFactory.getLogger(CheckoutController.class);

    private final CartItemRepository cartItemRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final FoodRepository foodRepository;

    @Value("${razorpay.key-id}")
    private String razorpayKeyId;

    @Value("${razorpay.key-secret}")
    private String razorpayKeySecret;

    public CheckoutController(
            CartItemRepository cartItemRepository,
            UserRepository userRepository,
            OrderRepository orderRepository,
            FoodRepository foodRepository) {

        this.cartItemRepository = cartItemRepository;
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
        this.foodRepository = foodRepository;
    }

    // ==================================================
    // 1. SHOW CHECKOUT PAGE
    // ==================================================

    @GetMapping("/checkout")
    public String checkout(
            HttpSession session,
            Model model,
            RedirectAttributes redirectAttributes) {

        Long userId = (Long) session.getAttribute("userId");

        if (userId == null) {
            return "redirect:/login";
        }

        User user = userRepository.findById(userId).orElse(null);

        if (user == null) {
            session.removeAttribute("userId");
            return "redirect:/login";
        }

        List<CartItem> cartItems =
                cartItemRepository.findByUserId(userId);

        if (cartItems == null || cartItems.isEmpty()) {
            return "redirect:/cart";
        }

        double cartTotal = 0;
        int cartCount = 0;

        for (CartItem item : cartItems) {

            if (item.getFood() == null || item.getQuantity() <= 0) {
                redirectAttributes.addFlashAttribute(
                        "paymentError",
                        "An item in your cart is invalid. Please review your cart."
                );

                return "redirect:/cart";
            }

            cartTotal += item.getTotalPrice();
            cartCount += item.getQuantity();
        }

        model.addAttribute("user", user);
        model.addAttribute("cartItems", cartItems);
        model.addAttribute("cartCount", cartCount);
        model.addAttribute("cartTotal", cartTotal);

        return "checkout";
    }

    // ==================================================
    // 2. PLACE ORDER: COD OR ONLINE
    // ==================================================

    @PostMapping("/checkout/place-order")
    @Transactional
    public String placeOrder(
            HttpSession session,
            @RequestParam("customerName") String customerName,
            @RequestParam("phone") String phone,
            @RequestParam("address") String address,
            @RequestParam("paymentMethod") String paymentMethod,
            Model model,
            RedirectAttributes redirectAttributes) {

        Long userId = (Long) session.getAttribute("userId");

        if (userId == null) {
            return "redirect:/login";
        }

        User user = userRepository.findById(userId).orElse(null);

        if (user == null) {
            return "redirect:/login";
        }

        List<CartItem> cartItems =
                cartItemRepository.findByUserId(userId);

        if (cartItems == null || cartItems.isEmpty()) {
            return "redirect:/cart";
        }

        boolean isCod = "COD".equalsIgnoreCase(paymentMethod);
        boolean isOnline = "ONLINE".equalsIgnoreCase(paymentMethod);

        if (!isCod && !isOnline) {
            redirectAttributes.addFlashAttribute(
                    "paymentError",
                    "Please select a valid payment method."
            );

            return "redirect:/checkout";
        }

        // Validate cart stock and calculate total.
        double totalAmount = 0;

        for (CartItem item : cartItems) {

            Food food = item.getFood();

            if (food == null
                    || item.getQuantity() <= 0
                    || food.getStock() < item.getQuantity()) {

                redirectAttributes.addFlashAttribute(
                        "paymentError",
                        "Some items are out of stock. Please review your cart."
                );

                return "redirect:/cart";
            }

            totalAmount += item.getTotalPrice();
        }

        if (!Double.isFinite(totalAmount) || totalAmount <= 0) {
            redirectAttributes.addFlashAttribute(
                    "paymentError",
                    "The order total must be greater than zero."
            );

            return "redirect:/cart";
        }

        long totalAmountPaise = Math.round(totalAmount * 100);

        if (totalAmountPaise <= 0
                || totalAmountPaise > Integer.MAX_VALUE) {

            redirectAttributes.addFlashAttribute(
                    "paymentError",
                    "The order amount is outside the supported payment range."
            );

            return "redirect:/checkout";
        }

        // Create local FoodHub order.
        Order order = new Order();

        order.setUser(user);
        order.setCustomerName(customerName);
        order.setPhone(phone);
        order.setAddress(address);
        order.setTotalAmount(totalAmount);
        order.setPaymentMethod(isCod ? "COD" : "ONLINE");
        order.setOrderDate(LocalDateTime.now());

        // Save order-item snapshots.
        for (CartItem cartItem : cartItems) {

            Food food = cartItem.getFood();

            OrderItem orderItem = new OrderItem(
                    order,
                    food,
                    food.getName(),
                    food.getPrice(),
                    cartItem.getQuantity()
            );

            order.addOrderItem(orderItem);
        }

        // ---------------- CASH ON DELIVERY ----------------

        if (isCod) {

            order.setStatus("PLACED");
            order.setPaymentStatus("NOT_REQUIRED");

            Order savedOrder = orderRepository.save(order);

            reduceStockAndClearCart(cartItems);

            logger.info(
                    "COD order placed successfully. orderId={}",
                    savedOrder.getId()
            );

            return "redirect:/order-success/" + savedOrder.getId();
        }

        // ---------------- ONLINE PAYMENT ----------------

        order.setStatus("PENDING");
        order.setPaymentStatus("PENDING");

        Order savedOrder = orderRepository.save(order);

        try {

            RazorpayClient razorpayClient =
                    new RazorpayClient(
                            razorpayKeyId,
                            razorpayKeySecret
                    );

            JSONObject request = new JSONObject();

            request.put("amount", (int) totalAmountPaise);
            request.put("currency", "INR");
            request.put("receipt", "foodhub_" + savedOrder.getId());

            com.razorpay.Order razorpayOrder =
                    razorpayClient.orders.create(request);

            String razorpayOrderId = razorpayOrder.get("id");

            if (razorpayOrderId == null
                    || razorpayOrderId.trim().isEmpty()) {

                throw new IllegalStateException(
                        "Razorpay did not return an order ID."
                );
            }

            savedOrder.setRazorpayOrderId(razorpayOrderId);
            orderRepository.save(savedOrder);

            // These attribute names must match razorpay-checkout.html.
            model.addAttribute("razorpayKeyId", razorpayKeyId);
            model.addAttribute("razorpayOrderId", razorpayOrderId);
            model.addAttribute("amount", (int) totalAmountPaise);
            model.addAttribute("localOrderId", savedOrder.getId());
            model.addAttribute("customerName", customerName);
            model.addAttribute("phone", phone);

            logger.info(
                    "Razorpay order created. localOrderId={}, razorpayOrderId={}, amountPaise={}",
                    savedOrder.getId(),
                    razorpayOrderId,
                    totalAmountPaise
            );

            return "razorpay-checkout";

        } catch (Exception e) {

            logger.error(
                    "Could not create Razorpay order. localOrderId={}",
                    savedOrder.getId(),
                    e
            );

            savedOrder.setStatus("PAYMENT_FAILED");
            savedOrder.setPaymentStatus("FAILED");
            orderRepository.save(savedOrder);

            redirectAttributes.addFlashAttribute(
                    "paymentError",
                    "Unable to start online payment. Please try again."
            );

            return "redirect:/checkout";
        }
    }

    // ==================================================
    // 3. VERIFY RAZORPAY PAYMENT
    // ==================================================

    @PostMapping("/checkout/verify-payment")
    @Transactional
    public String verifyPayment(
            @RequestParam("localOrderId") Long localOrderId,
            @RequestParam("razorpay_order_id") String razorpayOrderId,
            @RequestParam("razorpay_payment_id") String razorpayPaymentId,
            @RequestParam("razorpay_signature") String razorpaySignature,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        logger.info(
                "RAZORPAY CALLBACK RECEIVED: localOrderId={}, razorpayOrderId={}",
                localOrderId,
                razorpayOrderId
        );

        Long userId = (Long) session.getAttribute("userId");

        if (userId == null) {
            logger.warn("Payment callback received without a logged-in user.");
            return "redirect:/login";
        }

        Order order = orderRepository
                .findById(localOrderId)
                .orElse(null);

        if (order == null
                || order.getUser() == null
                || order.getUser().getId() == null
                || !order.getUser().getId().equals(userId)) {

            logger.error(
                    "Order missing or ownership check failed. localOrderId={}",
                    localOrderId
            );

            return "redirect:/orders";
        }

        // Repeated callbacks must not process the order twice.
        if ("PAID".equalsIgnoreCase(order.getPaymentStatus())) {

            logger.info(
                    "Order {} has already been marked PAID.",
                    localOrderId
            );

            return "redirect:/order-success/" + order.getId();
        }

        // Confirm this is the expected pending online order.
        if (!"ONLINE".equalsIgnoreCase(order.getPaymentMethod())
                || !"PENDING".equalsIgnoreCase(order.getPaymentStatus())
                || order.getRazorpayOrderId() == null
                || !order.getRazorpayOrderId().equals(razorpayOrderId)) {

            logger.error(
                    "Order/payment matching failed. localOrderId={}, receivedOrderId={}, savedOrderId={}",
                    localOrderId,
                    razorpayOrderId,
                    order.getRazorpayOrderId()
            );

            redirectAttributes.addFlashAttribute(
                    "paymentError",
                    "Payment details could not be matched with your order. If money was deducted, please contact support."
            );

            return "redirect:/orders";
        }

        // ---------------- STEP 1: VERIFY SIGNATURE ----------------

        try {

            JSONObject signatureOptions = new JSONObject();

            signatureOptions.put(
                    "razorpay_order_id",
                    order.getRazorpayOrderId()
            );

            signatureOptions.put(
                    "razorpay_payment_id",
                    razorpayPaymentId
            );

            signatureOptions.put(
                    "razorpay_signature",
                    razorpaySignature
            );

            boolean signatureValid = Utils.verifyPaymentSignature(
                    signatureOptions,
                    razorpayKeySecret
            );

            if (!signatureValid) {

                logger.error(
                        "Razorpay signature verification returned false. " +
                        "localOrderId={}, razorpayOrderId={}, paymentId={}",
                        localOrderId,
                        razorpayOrderId,
                        razorpayPaymentId
                );

                redirectAttributes.addFlashAttribute(
                        "paymentError",
                        "Payment signature verification failed. Check your Razorpay key configuration. If money was deducted, please contact support."
                );

                return "redirect:/orders";
            }

            logger.info(
                    "Razorpay signature verified successfully. localOrderId={}",
                    localOrderId
            );

        } catch (Exception e) {

            logger.error(
                    "Signature verification exception. localOrderId={}",
                    localOrderId,
                    e
            );

            redirectAttributes.addFlashAttribute(
                    "paymentError",
                    "Unable to verify the payment. If money was deducted, please contact support."
            );

            return "redirect:/orders";
        }

        // ---------------- STEP 2: FETCH PAYMENT FROM RAZORPAY ----------------

        String paymentStatus;

        long expectedAmountPaise =
                Math.round(order.getTotalAmount() * 100);

        try {

            RazorpayClient razorpayClient =
                    new RazorpayClient(
                            razorpayKeyId,
                            razorpayKeySecret
                    );

            com.razorpay.Payment payment =
                    razorpayClient.payments.fetch(razorpayPaymentId);

            String paymentOrderId = payment.get("order_id");
            String currency = payment.get("currency");
            Object amountValue = payment.get("amount");

            paymentStatus = payment.get("status");

            logger.info(
                    "Razorpay payment details: localOrderId={}, paymentOrderId={}, status={}, currency={}, amount={}",
                    localOrderId,
                    paymentOrderId,
                    paymentStatus,
                    currency,
                    amountValue
            );

            if (!(amountValue instanceof Number)) {
                throw new IllegalStateException(
                        "Razorpay returned an invalid payment amount."
                );
            }

            long actualAmountPaise =
                    ((Number) amountValue).longValue();

            if (!razorpayOrderId.equals(paymentOrderId)
                    || actualAmountPaise != expectedAmountPaise
                    || !"INR".equalsIgnoreCase(currency)) {

                throw new IllegalStateException(
                        "Payment order ID, amount, or currency mismatch."
                );
            }

        } catch (Exception e) {

            logger.error(
                    "Unable to fetch or validate Razorpay payment. localOrderId={}",
                    localOrderId,
                    e
            );

            redirectAttributes.addFlashAttribute(
                    "paymentError",
                    "We could not confirm your payment with Razorpay. Check your order status before retrying. If money was deducted, please contact support."
            );

            return "redirect:/orders";
        }

        // Only captured payments can be marked PAID.
        if (!"captured".equalsIgnoreCase(paymentStatus)) {

            logger.warn(
                    "Razorpay payment is not captured. localOrderId={}, status={}",
                    localOrderId,
                    paymentStatus
            );

            redirectAttributes.addFlashAttribute(
                    "paymentError",
                    "Razorpay has not confirmed the payment as captured. Please check your payment status."
            );

            return "redirect:/orders";
        }

        // ---------------- STEP 3: VALIDATE ORDER ITEMS ----------------

        List<OrderItem> orderItems = order.getOrderItems();

        if (orderItems == null || orderItems.isEmpty()) {

            logger.error(
                    "Captured payment but order has no items. localOrderId={}",
                    localOrderId
            );

            order.setRazorpayPaymentId(razorpayPaymentId);
            order.setPaymentStatus("PAID");
            order.setStatus("PAYMENT_REVIEW");

            orderRepository.save(order);

            redirectAttributes.addFlashAttribute(
                    "paymentError",
                    "Payment was received, but your order requires review. Please contact support."
            );

            return "redirect:/orders";
        }

        // ---------------- STEP 4: CHECK STOCK ----------------

        for (OrderItem orderItem : orderItems) {

            Food food = orderItem.getFood();

            if (food == null
                    || orderItem.getQuantity() <= 0
                    || food.getStock() < orderItem.getQuantity()) {

                logger.error(
                        "Captured payment but stock review is required. localOrderId={}",
                        localOrderId
                );

                order.setRazorpayPaymentId(razorpayPaymentId);
                order.setPaymentStatus("PAID");
                order.setStatus("PAYMENT_REVIEW");

                orderRepository.save(order);

                redirectAttributes.addFlashAttribute(
                        "paymentError",
                        "Payment was received, but your order requires stock review. Please contact support."
                );

                return "redirect:/orders";
            }
        }

        // ---------------- STEP 5: SAVE SUCCESSFUL PAYMENT ----------------

        order.setRazorpayPaymentId(razorpayPaymentId);
        order.setPaymentStatus("PAID");
        order.setStatus("PLACED");

        orderRepository.save(order);

        // ---------------- STEP 6: REDUCE STOCK ----------------

        for (OrderItem orderItem : orderItems) {

            Food food = orderItem.getFood();

            food.setStock(
                    food.getStock() - orderItem.getQuantity()
            );

            foodRepository.save(food);
        }

        // ---------------- STEP 7: REMOVE PURCHASED CART ITEMS ----------------

        removeOrderedQuantitiesFromCart(userId, orderItems);

        logger.info(
                "ONLINE ORDER SUCCESS: orderId={}, paymentStatus=PAID, status=PLACED",
                localOrderId
        );

        // ---------------- STEP 8: REDIRECT TO SUCCESS PAGE ----------------

        return "redirect:/order-success/" + order.getId();
    }

    // ==================================================
    // 4. REDUCE STOCK AND CLEAR CART FOR COD
    // ==================================================

    private void reduceStockAndClearCart(List<CartItem> cartItems) {

        for (CartItem item : cartItems) {

            Food food = item.getFood();

            food.setStock(
                    food.getStock() - item.getQuantity()
            );

            foodRepository.save(food);
        }

        cartItemRepository.deleteAll(cartItems);
    }

    // ==================================================
    // 5. REMOVE PURCHASED QUANTITIES FROM CART
    // ==================================================

    private void removeOrderedQuantitiesFromCart(
            Long userId,
            List<OrderItem> orderItems) {

        List<CartItem> currentCart =
                cartItemRepository.findByUserId(userId);

        for (CartItem cartItem : currentCart) {

            if (cartItem.getFood() == null) {
                continue;
            }

            Long cartFoodId = cartItem.getFood().getId();

            for (OrderItem orderItem : orderItems) {

                if (orderItem.getFood() == null
                        || orderItem.getFood().getId() == null
                        || !orderItem.getFood().getId().equals(cartFoodId)) {
                    continue;
                }

                int cartQuantity = cartItem.getQuantity();
                int orderedQuantity = orderItem.getQuantity();

                if (cartQuantity == orderedQuantity) {

                    cartItemRepository.delete(cartItem);

                } else if (cartQuantity > orderedQuantity) {

                    cartItem.setQuantity(
                            cartQuantity - orderedQuantity
                    );

                    cartItemRepository.save(cartItem);
                }

                break;
            }
        }
    }

    // ==================================================
    // 6. ORDER SUCCESS PAGE
    // ==================================================

    @GetMapping("/order-success/{orderId}")
    public String orderSuccess(
            @PathVariable Long orderId,
            HttpSession session,
            Model model) {

        Long userId = (Long) session.getAttribute("userId");

        if (userId == null) {
            return "redirect:/login";
        }

        Order order = orderRepository
                .findById(orderId)
                .orElse(null);

        if (order == null
                || order.getUser() == null
                || order.getUser().getId() == null
                || !order.getUser().getId().equals(userId)) {

            return "redirect:/orders";
        }

        if ("ONLINE".equalsIgnoreCase(order.getPaymentMethod())
                && !"PAID".equalsIgnoreCase(order.getPaymentStatus())) {

            logger.warn(
                    "Success page blocked because payment is not PAID. orderId={}, paymentStatus={}",
                    orderId,
                    order.getPaymentStatus()
            );

            return "redirect:/orders";
        }

        model.addAttribute("order", order);

        return "order-success";
    }
}