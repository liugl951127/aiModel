package com.aiplatform.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 业务实体 (客户/洽谈/商机/合同/报价/订单/产品/服务/回款/费用) — 共 10 个, 都在这里.
 * MyBatis Plus 自动识别 @TableName 映射表.
 */
public class BizEntities {

    @Data
    @TableName("biz_customer")
    public static class Customer {
        @TableId(type = IdType.ASSIGN_ID) private Long id;
        private Long tenantId;
        private String name;
        private String industry;
        private String scale;          // 小/中/大/超
        private String contactName;
        private String contactPhone;
        private String contactEmail;
        private String address;
        private String source;         // 官网/活动/推荐/广告
        private String level;          // S/A/B/C
        private Integer status;        // 1=正常 0=停用
        private Long ownerUserId;
        private String notes;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @TableName("biz_chat")
    public static class Chat {
        @TableId(type = IdType.ASSIGN_ID) private Long id;
        private Long tenantId;
        private Long customerId;
        private Long ownerUserId;
        private String subject;
        private String type;           // 面谈/电话/微信/邮件
        private String status;         // 进行中/已成交/已搁置
        private String nextStep;
        private LocalDateTime nextDate;
        private String summary;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @TableName("biz_opportunity")
    public static class Opportunity {
        @TableId(type = IdType.ASSIGN_ID) private Long id;
        private Long tenantId;
        private Long customerId;
        private String name;
        private BigDecimal amount;
        private String currency;
        private String stage;          // 线索/接触/方案/谈判/成交/输单
        private Integer probability;    // 0-100
        private LocalDateTime expectedDate;
        private String source;
        private Long ownerUserId;
        private String products;        // JSON
        private String notes;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @TableName("biz_quote")
    public static class Quote {
        @TableId(type = IdType.ASSIGN_ID) private Long id;
        private Long tenantId;
        private Long customerId;
        private Long opportunityId;
        private String code;
        private String title;
        private BigDecimal totalAmount;
        private BigDecimal discount;
        private BigDecimal finalAmount;
        private String currency;
        private LocalDateTime validUntil;
        private String status;          // 草稿/审批中/已发送/已接受/已拒绝
        private String items;           // JSON
        private String notes;
        private Long createdBy;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @TableName("biz_contract")
    public static class Contract {
        @TableId(type = IdType.ASSIGN_ID) private Long id;
        private Long tenantId;
        private Long customerId;
        private Long opportunityId;
        private Long quoteId;
        private String code;
        private String title;
        private BigDecimal amount;
        private String currency;
        private LocalDateTime signDate;
        private LocalDateTime startDate;
        private LocalDateTime endDate;
        private String status;          // 草签/执行中/已完结/已终止
        private String paymentTerms;
        private String attachments;     // JSON
        private String notes;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @TableName("biz_order")
    public static class Order {
        @TableId(type = IdType.ASSIGN_ID) private Long id;
        private Long tenantId;
        private Long customerId;
        private Long contractId;
        private String code;
        private BigDecimal amount;
        private BigDecimal paid;
        private String status;          // 待付款/部分付款/已付款/已发货/已完成
        private LocalDateTime deliveryDate;
        private String notes;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @TableName("biz_payment")
    public static class Payment {
        @TableId(type = IdType.ASSIGN_ID) private Long id;
        private Long tenantId;
        private Long orderId;
        private BigDecimal amount;
        private String method;          // 银行转账/支付宝/微信
        private String status;          // 待收款/已收款/已退款
        private LocalDateTime paidAt;
        private String notes;
        private LocalDateTime createdAt;
    }

    @Data
    @TableName("biz_product")
    public static class Product {
        @TableId(type = IdType.ASSIGN_ID) private Long id;
        private Long tenantId;
        private String code;
        private String name;
        private String category;
        private BigDecimal price;
        private String unit;
        private String description;
        private Integer status;
        private Integer stock;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @TableName("biz_service")
    public static class Service {
        @TableId(type = IdType.ASSIGN_ID) private Long id;
        private Long tenantId;
        private String code;
        private String name;
        private String category;
        private BigDecimal price;
        private String description;
        private Integer slaHours;
        private Integer status;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @TableName("biz_expense")
    public static class Expense {
        @TableId(type = IdType.ASSIGN_ID) private Long id;
        private Long tenantId;
        private Long orderId;
        private String category;
        private BigDecimal amount;
        private LocalDateTime happenedAt;
        private String notes;
        private Long createdBy;
        private LocalDateTime createdAt;
    }
}
