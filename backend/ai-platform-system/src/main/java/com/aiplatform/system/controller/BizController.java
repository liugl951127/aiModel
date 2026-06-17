package com.aiplatform.system.controller;

import com.aiplatform.common.exception.BusinessException;
import com.aiplatform.common.result.Result;
import com.aiplatform.common.result.ResultCode;
import com.aiplatform.system.entity.BizEntities.*;
import com.aiplatform.system.mapper.BizMappers.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 业务表通用 CRUD — 客户/洽谈/商机/合同/报价/订单/产品/服务/回款/费用.
 *
 * <p>设计: 1 个 controller 覆盖 10 张表, 减少文件数. 每张表挂在一个子路径:
 * <ul>
 *   <li>/api/biz/customer    - 客户
 *   <li>/api/biz/chat       - 洽谈
 *   <li>/api/biz/opportunity- 商机
 *   <li>/api/biz/quote      - 报价
 *   <li>/api/biz/contract   - 合同
 *   <li>/api/biz/order      - 订单
 *   <li>/api/biz/payment    - 回款
 *   <li>/api/biz/product    - 产品
 *   <li>/api/biz/service    - 服务
 *   <li>/api/biz/expense    - 费用
 * </ul>
 *
 * <p>统一接口:
 * <ul>
 *   <li>GET  /{name}/page    分页 (params: page, size, keyword)
 *   <li>GET  /{name}/list    全量列表 (轻量, 用于下拉)
 *   <li>GET  /{name}/{id}    单个
 *   <li>POST /{name}         新增
 *   <li>PUT  /{name}         更新
 *   <li>DELETE /{name}/{id}  删除
 *   <li>GET  /{name}/stats   统计 (数量 + 总额)
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/biz")
@RequiredArgsConstructor
public class BizController {

    private final CustomerMapper customerMapper;
    private final ChatMapper chatMapper;
    private final OpportunityMapper opportunityMapper;
    private final QuoteMapper quoteMapper;
    private final ContractMapper contractMapper;
    private final OrderMapper orderMapper;
    private final PaymentMapper paymentMapper;
    private final ProductMapper productMapper;
    private final ServiceMapper serviceMapper;
    private final ExpenseMapper expenseMapper;

    // ============== 客户 ==============
    @GetMapping("/customer/page")
    public Result<IPage<Customer>> customerPage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword) {
        Page<Customer> p = new Page<>(page, size);
        LambdaQueryWrapper<Customer> w = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            w.like(Customer::getName, keyword).or().like(Customer::getContactName, keyword);
        }
        w.orderByDesc(Customer::getId);
        return Result.success(customerMapper.selectPage(p, w));
    }

    @GetMapping("/customer/list")
    public Result<List<Customer>> customerList() {
        return Result.success(customerMapper.selectList(
                new LambdaQueryWrapper<Customer>().eq(Customer::getStatus, 1).orderByAsc(Customer::getName)));
    }

    @GetMapping("/customer/{id}")
    public Result<Customer> customerGet(@PathVariable Long id) {
        Customer c = customerMapper.selectById(id);
        if (c == null) throw new BusinessException(ResultCode.NOT_FOUND);
        return Result.success(c);
    }

    @PostMapping("/customer")
    @Transactional
    public Result<Customer> customerCreate(@RequestBody Customer body) {
        if (body.getTenantId() == null) body.setTenantId(1L);
        if (body.getStatus() == null) body.setStatus(1);
        if (body.getLevel() == null) body.setLevel("C");
        customerMapper.insert(body);
        return Result.success(body);
    }

    @PutMapping("/customer")
    @Transactional
    public Result<Customer> customerUpdate(@RequestBody Customer body) {
        if (body.getId() == null) throw new BusinessException(ResultCode.BAD_REQUEST, "id required");
        customerMapper.updateById(body);
        return Result.success(body);
    }

    @DeleteMapping("/customer/{id}")
    @Transactional
    public Result<Void> customerDelete(@PathVariable Long id) {
        customerMapper.deleteById(id);
        return Result.success(null);
    }

    @GetMapping("/customer/stats")
    public Result<Map<String, Object>> customerStats() {
        Map<String, Object> r = new LinkedHashMap<>();
        Long total = customerMapper.selectCount(null);
        r.put("total", total);
        // 按 level 分组
        Map<String, Long> byLevel = new LinkedHashMap<>();
        for (String lvl : new String[]{"S", "A", "B", "C"}) {
            byLevel.put(lvl, customerMapper.selectCount(new LambdaQueryWrapper<Customer>().eq(Customer::getLevel, lvl)));
        }
        r.put("byLevel", byLevel);
        return Result.success(r);
    }

    // ============== 洽谈 ==============
    @GetMapping("/chat/page")
    public Result<IPage<Chat>> chatPage(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int size, @RequestParam(required = false) String keyword) {
        Page<Chat> p = new Page<>(page, size);
        LambdaQueryWrapper<Chat> w = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) w.like(Chat::getSubject, keyword);
        w.orderByDesc(Chat::getId);
        return Result.success(chatMapper.selectPage(p, w));
    }

    @GetMapping("/chat/list")
    public Result<List<Chat>> chatList() {
        return Result.success(chatMapper.selectList(new LambdaQueryWrapper<Chat>().orderByDesc(Chat::getId)));
    }

    @GetMapping("/chat/{id}")
    public Result<Chat> chatGet(@PathVariable Long id) { return Result.success(chatMapper.selectById(id)); }

    @PostMapping("/chat")
    @Transactional
    public Result<Chat> chatCreate(@RequestBody Chat body) {
        if (body.getTenantId() == null) body.setTenantId(1L);
        if (body.getStatus() == null) body.setStatus("进行中");
        chatMapper.insert(body);
        return Result.success(body);
    }

    @PutMapping("/chat")
    @Transactional
    public Result<Chat> chatUpdate(@RequestBody Chat body) {
        if (body.getId() == null) throw new BusinessException(ResultCode.BAD_REQUEST, "id required");
        chatMapper.updateById(body);
        return Result.success(body);
    }

    @DeleteMapping("/chat/{id}")
    @Transactional
    public Result<Void> chatDelete(@PathVariable Long id) { chatMapper.deleteById(id); return Result.success(null); }

    // ============== 商机 ==============
    @GetMapping("/opportunity/page")
    public Result<IPage<Opportunity>> opportunityPage(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int size, @RequestParam(required = false) String keyword) {
        Page<Opportunity> p = new Page<>(page, size);
        LambdaQueryWrapper<Opportunity> w = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) w.like(Opportunity::getName, keyword);
        w.orderByDesc(Opportunity::getId);
        return Result.success(opportunityMapper.selectPage(p, w));
    }

    @GetMapping("/opportunity/list")
    public Result<List<Opportunity>> opportunityList() {
        return Result.success(opportunityMapper.selectList(new LambdaQueryWrapper<Opportunity>().orderByDesc(Opportunity::getId)));
    }

    @GetMapping("/opportunity/{id}")
    public Result<Opportunity> opportunityGet(@PathVariable Long id) { return Result.success(opportunityMapper.selectById(id)); }

    @PostMapping("/opportunity")
    @Transactional
    public Result<Opportunity> opportunityCreate(@RequestBody Opportunity body) {
        if (body.getTenantId() == null) body.setTenantId(1L);
        if (body.getStage() == null) body.setStage("线索");
        if (body.getProbability() == null) body.setProbability(10);
        if (body.getAmount() == null) body.setAmount(BigDecimal.ZERO);
        opportunityMapper.insert(body);
        return Result.success(body);
    }

    @PutMapping("/opportunity")
    @Transactional
    public Result<Opportunity> opportunityUpdate(@RequestBody Opportunity body) {
        if (body.getId() == null) throw new BusinessException(ResultCode.BAD_REQUEST, "id required");
        opportunityMapper.updateById(body);
        return Result.success(body);
    }

    @DeleteMapping("/opportunity/{id}")
    @Transactional
    public Result<Void> opportunityDelete(@PathVariable Long id) { opportunityMapper.deleteById(id); return Result.success(null); }

    @GetMapping("/opportunity/stats")
    public Result<Map<String, Object>> opportunityStats() {
        Map<String, Object> r = new LinkedHashMap<>();
        // 按 stage 分组 + 金额合计
        Map<String, Map<String, Object>> byStage = new LinkedHashMap<>();
        for (String stg : new String[]{"线索", "接触", "方案", "谈判", "成交", "输单"}) {
            List<Opportunity> all = opportunityMapper.selectList(new LambdaQueryWrapper<Opportunity>().eq(Opportunity::getStage, stg));
            BigDecimal total = all.stream().map(Opportunity::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("count", all.size());
            m.put("amount", total);
            byStage.put(stg, m);
        }
        r.put("byStage", byStage);
        return Result.success(r);
    }

    // ============== 报价 ==============
    @GetMapping("/quote/page")
    public Result<IPage<Quote>> quotePage(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int size, @RequestParam(required = false) String keyword) {
        Page<Quote> p = new Page<>(page, size);
        LambdaQueryWrapper<Quote> w = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) w.like(Quote::getTitle, keyword).or().like(Quote::getCode, keyword);
        w.orderByDesc(Quote::getId);
        return Result.success(quoteMapper.selectPage(p, w));
    }

    @GetMapping("/quote/list")
    public Result<List<Quote>> quoteList() {
        return Result.success(quoteMapper.selectList(new LambdaQueryWrapper<Quote>().orderByDesc(Quote::getId)));
    }

    @GetMapping("/quote/{id}")
    public Result<Quote> quoteGet(@PathVariable Long id) { return Result.success(quoteMapper.selectById(id)); }

    @PostMapping("/quote")
    @Transactional
    public Result<Quote> quoteCreate(@RequestBody Quote body) {
        if (body.getTenantId() == null) body.setTenantId(1L);
        if (body.getStatus() == null) body.setStatus("草稿");
        if (body.getCurrency() == null) body.setCurrency("CNY");
        if (body.getCode() == null || body.getCode().isBlank()) body.setCode("Q-" + System.currentTimeMillis());
        if (body.getFinalAmount() == null && body.getTotalAmount() != null && body.getDiscount() != null) {
            body.setFinalAmount(body.getTotalAmount().multiply(BigDecimal.ONE.subtract(body.getDiscount())));
        }
        quoteMapper.insert(body);
        return Result.success(body);
    }

    @PutMapping("/quote")
    @Transactional
    public Result<Quote> quoteUpdate(@RequestBody Quote body) {
        if (body.getId() == null) throw new BusinessException(ResultCode.BAD_REQUEST, "id required");
        quoteMapper.updateById(body);
        return Result.success(body);
    }

    @DeleteMapping("/quote/{id}")
    @Transactional
    public Result<Void> quoteDelete(@PathVariable Long id) { quoteMapper.deleteById(id); return Result.success(null); }

    // ============== 合同 ==============
    @GetMapping("/contract/page")
    public Result<IPage<Contract>> contractPage(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int size, @RequestParam(required = false) String keyword) {
        Page<Contract> p = new Page<>(page, size);
        LambdaQueryWrapper<Contract> w = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) w.like(Contract::getTitle, keyword).or().like(Contract::getCode, keyword);
        w.orderByDesc(Contract::getId);
        return Result.success(contractMapper.selectPage(p, w));
    }

    @GetMapping("/contract/list")
    public Result<List<Contract>> contractList() {
        return Result.success(contractMapper.selectList(new LambdaQueryWrapper<Contract>().orderByDesc(Contract::getId)));
    }

    @GetMapping("/contract/{id}")
    public Result<Contract> contractGet(@PathVariable Long id) { return Result.success(contractMapper.selectById(id)); }

    @PostMapping("/contract")
    @Transactional
    public Result<Contract> contractCreate(@RequestBody Contract body) {
        if (body.getTenantId() == null) body.setTenantId(1L);
        if (body.getStatus() == null) body.setStatus("执行中");
        if (body.getCode() == null || body.getCode().isBlank()) body.setCode("C-" + System.currentTimeMillis());
        contractMapper.insert(body);
        return Result.success(body);
    }

    @PutMapping("/contract")
    @Transactional
    public Result<Contract> contractUpdate(@RequestBody Contract body) {
        if (body.getId() == null) throw new BusinessException(ResultCode.BAD_REQUEST, "id required");
        contractMapper.updateById(body);
        return Result.success(body);
    }

    @DeleteMapping("/contract/{id}")
    @Transactional
    public Result<Void> contractDelete(@PathVariable Long id) { contractMapper.deleteById(id); return Result.success(null); }

    // ============== 订单 ==============
    @GetMapping("/order/page")
    public Result<IPage<Order>> orderPage(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int size, @RequestParam(required = false) String keyword) {
        Page<Order> p = new Page<>(page, size);
        LambdaQueryWrapper<Order> w = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) w.like(Order::getCode, keyword);
        w.orderByDesc(Order::getId);
        return Result.success(orderMapper.selectPage(p, w));
    }

    @GetMapping("/order/list")
    public Result<List<Order>> orderList() {
        return Result.success(orderMapper.selectList(new LambdaQueryWrapper<Order>().orderByDesc(Order::getId)));
    }

    @GetMapping("/order/{id}")
    public Result<Order> orderGet(@PathVariable Long id) { return Result.success(orderMapper.selectById(id)); }

    @PostMapping("/order")
    @Transactional
    public Result<Order> orderCreate(@RequestBody Order body) {
        if (body.getTenantId() == null) body.setTenantId(1L);
        if (body.getStatus() == null) body.setStatus("待付款");
        if (body.getAmount() == null) body.setAmount(BigDecimal.ZERO);
        if (body.getPaid() == null) body.setPaid(BigDecimal.ZERO);
        if (body.getCode() == null || body.getCode().isBlank()) body.setCode("O-" + System.currentTimeMillis());
        orderMapper.insert(body);
        return Result.success(body);
    }

    @PutMapping("/order")
    @Transactional
    public Result<Order> orderUpdate(@RequestBody Order body) {
        if (body.getId() == null) throw new BusinessException(ResultCode.BAD_REQUEST, "id required");
        orderMapper.updateById(body);
        return Result.success(body);
    }

    @DeleteMapping("/order/{id}")
    @Transactional
    public Result<Void> orderDelete(@PathVariable Long id) { orderMapper.deleteById(id); return Result.success(null); }

    // ============== 回款 ==============
    @GetMapping("/payment/page")
    public Result<IPage<Payment>> paymentPage(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int size) {
        Page<Payment> p = new Page<>(page, size);
        return Result.success(paymentMapper.selectPage(p, new LambdaQueryWrapper<Payment>().orderByDesc(Payment::getId)));
    }

    @GetMapping("/payment/list")
    public Result<List<Payment>> paymentList() {
        return Result.success(paymentMapper.selectList(new LambdaQueryWrapper<Payment>().orderByDesc(Payment::getId)));
    }

    @PostMapping("/payment")
    @Transactional
    public Result<Payment> paymentCreate(@RequestBody Payment body) {
        if (body.getTenantId() == null) body.setTenantId(1L);
        if (body.getStatus() == null) body.setStatus("已收款");
        paymentMapper.insert(body);
        // 自动更新订单已付金额
        if (body.getOrderId() != null) {
            Order o = orderMapper.selectById(body.getOrderId());
            if (o != null) {
                BigDecimal newPaid = (o.getPaid() == null ? BigDecimal.ZERO : o.getPaid()).add(body.getAmount());
                o.setPaid(newPaid);
                if (o.getAmount() != null && newPaid.compareTo(o.getAmount()) >= 0) o.setStatus("已付款");
                else if (newPaid.compareTo(BigDecimal.ZERO) > 0) o.setStatus("部分付款");
                orderMapper.updateById(o);
            }
        }
        return Result.success(body);
    }

    @DeleteMapping("/payment/{id}")
    @Transactional
    public Result<Void> paymentDelete(@PathVariable Long id) { paymentMapper.deleteById(id); return Result.success(null); }

    // ============== 产品 ==============
    @GetMapping("/product/page")
    public Result<IPage<Product>> productPage(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int size, @RequestParam(required = false) String keyword) {
        Page<Product> p = new Page<>(page, size);
        LambdaQueryWrapper<Product> w = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) w.like(Product::getName, keyword).or().like(Product::getCode, keyword);
        w.orderByDesc(Product::getId);
        return Result.success(productMapper.selectPage(p, w));
    }

    @GetMapping("/product/list")
    public Result<List<Product>> productList() {
        return Result.success(productMapper.selectList(new LambdaQueryWrapper<Product>().eq(Product::getStatus, 1).orderByAsc(Product::getName)));
    }

    @GetMapping("/product/{id}")
    public Result<Product> productGet(@PathVariable Long id) { return Result.success(productMapper.selectById(id)); }

    @PostMapping("/product")
    @Transactional
    public Result<Product> productCreate(@RequestBody Product body) {
        if (body.getTenantId() == null) body.setTenantId(1L);
        if (body.getStatus() == null) body.setStatus(1);
        if (body.getStock() == null) body.setStock(0);
        if (body.getPrice() == null) body.setPrice(BigDecimal.ZERO);
        productMapper.insert(body);
        return Result.success(body);
    }

    @PutMapping("/product")
    @Transactional
    public Result<Product> productUpdate(@RequestBody Product body) {
        if (body.getId() == null) throw new BusinessException(ResultCode.BAD_REQUEST, "id required");
        productMapper.updateById(body);
        return Result.success(body);
    }

    @DeleteMapping("/product/{id}")
    @Transactional
    public Result<Void> productDelete(@PathVariable Long id) { productMapper.deleteById(id); return Result.success(null); }

    // ============== 服务 ==============
    @GetMapping("/service/page")
    public Result<IPage<Service>> servicePage(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int size, @RequestParam(required = false) String keyword) {
        Page<Service> p = new Page<>(page, size);
        LambdaQueryWrapper<Service> w = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) w.like(Service::getName, keyword);
        w.orderByDesc(Service::getId);
        return Result.success(serviceMapper.selectPage(p, w));
    }

    @GetMapping("/service/list")
    public Result<List<Service>> serviceList() {
        return Result.success(serviceMapper.selectList(new LambdaQueryWrapper<Service>().eq(Service::getStatus, 1).orderByAsc(Service::getName)));
    }

    @GetMapping("/service/{id}")
    public Result<Service> serviceGet(@PathVariable Long id) { return Result.success(serviceMapper.selectById(id)); }

    @PostMapping("/service")
    @Transactional
    public Result<Service> serviceCreate(@RequestBody Service body) {
        if (body.getTenantId() == null) body.setTenantId(1L);
        if (body.getStatus() == null) body.setStatus(1);
        if (body.getPrice() == null) body.setPrice(BigDecimal.ZERO);
        if (body.getSlaHours() == null) body.setSlaHours(24);
        serviceMapper.insert(body);
        return Result.success(body);
    }

    @PutMapping("/service")
    @Transactional
    public Result<Service> serviceUpdate(@RequestBody Service body) {
        if (body.getId() == null) throw new BusinessException(ResultCode.BAD_REQUEST, "id required");
        serviceMapper.updateById(body);
        return Result.success(body);
    }

    @DeleteMapping("/service/{id}")
    @Transactional
    public Result<Void> serviceDelete(@PathVariable Long id) { serviceMapper.deleteById(id); return Result.success(null); }

    // ============== 费用 ==============
    @GetMapping("/expense/page")
    public Result<IPage<Expense>> expensePage(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int size) {
        Page<Expense> p = new Page<>(page, size);
        return Result.success(expenseMapper.selectPage(p, new LambdaQueryWrapper<Expense>().orderByDesc(Expense::getId)));
    }

    @PostMapping("/expense")
    @Transactional
    public Result<Expense> expenseCreate(@RequestBody Expense body) {
        if (body.getTenantId() == null) body.setTenantId(1L);
        expenseMapper.insert(body);
        return Result.success(body);
    }

    @DeleteMapping("/expense/{id}")
    @Transactional
    public Result<Void> expenseDelete(@PathVariable Long id) { expenseMapper.deleteById(id); return Result.success(null); }

    /**
     * 工作台业务指标聚合 (Dashboard.vue 用).
     * <p>返回 4 大指标: 客户数 / 商机金额 / 订单数 / 回款金额, 加 7 项业务表条数.</p>
     */
    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard() {
        Map<String, Object> r = new LinkedHashMap<>();
        // 4 大核心指标 (Dashboard.vue 前端用的字段名)
        r.put("customerTotal", Optional.ofNullable(customerMapper.selectCount(null)).orElse(0L));
        r.put("opportunityTotal", Optional.ofNullable(opportunityMapper.selectCount(null)).orElse(0L));
        r.put("orderTotal", Optional.ofNullable(orderMapper.selectCount(null)).orElse(0L));
        r.put("paidAmount", paymentMapper.selectList(null).stream()
                .map(Payment::getAmount).filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        // 合同金额合计 (Dashboard 详情面板用)
        r.put("contractAmount", contractMapper.selectList(null).stream()
                .map(Contract::getAmount).filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        // 商机金额合计 (备用)
        r.put("opportunityAmount", opportunityMapper.selectList(null).stream()
                .map(Opportunity::getAmount).filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        // 7 项业务表条数 (供 Dashboard 详细面板)
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("customer", Optional.ofNullable(customerMapper.selectCount(null)).orElse(0L));
        counts.put("chat", Optional.ofNullable(chatMapper.selectCount(null)).orElse(0L));
        counts.put("opportunity", Optional.ofNullable(opportunityMapper.selectCount(null)).orElse(0L));
        counts.put("quote", Optional.ofNullable(quoteMapper.selectCount(null)).orElse(0L));
        counts.put("contract", Optional.ofNullable(contractMapper.selectCount(null)).orElse(0L));
        counts.put("order", Optional.ofNullable(orderMapper.selectCount(null)).orElse(0L));
        counts.put("payment", Optional.ofNullable(paymentMapper.selectCount(null)).orElse(0L));
        r.put("tableCounts", counts);
        // 商机阶段分布
        Map<String, Long> stages = new LinkedHashMap<>();
        opportunityMapper.selectList(null).forEach(o -> {
            String s = o.getStage() == null ? "未知" : o.getStage();
            stages.merge(s, 1L, Long::sum);
        });
        r.put("opportunityStages", stages);
        return Result.success(r);
    }
}
