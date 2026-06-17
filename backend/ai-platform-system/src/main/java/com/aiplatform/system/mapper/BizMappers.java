package com.aiplatform.system.mapper;

import com.aiplatform.system.entity.BizEntities.*;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

public class BizMappers {
    @Mapper public interface CustomerMapper extends BaseMapper<Customer> {}
    @Mapper public interface ChatMapper extends BaseMapper<Chat> {}
    @Mapper public interface OpportunityMapper extends BaseMapper<Opportunity> {}
    @Mapper public interface QuoteMapper extends BaseMapper<Quote> {}
    @Mapper public interface ContractMapper extends BaseMapper<Contract> {}
    @Mapper public interface OrderMapper extends BaseMapper<Order> {}
    @Mapper public interface PaymentMapper extends BaseMapper<Payment> {}
    @Mapper public interface ProductMapper extends BaseMapper<Product> {}
    @Mapper public interface ServiceMapper extends BaseMapper<Service> {}
    @Mapper public interface ExpenseMapper extends BaseMapper<Expense> {}
}
