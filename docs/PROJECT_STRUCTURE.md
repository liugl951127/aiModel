# 项目结构与企业级规范

## 1. 分层原则

| 层 | 模块 | 依赖 | 备注 |
| --- | --- | --- | --- |
| **基础** | `common` | 无 Spring 运行时 | 只用 JJWT + fastjson2 + hutool-core |
| **配置中心** | `dependencies` | — | 预留 BOM |
| **共享 API** | `api` | openfeign（optional） | 跨服务 DTO |
| **Starter** | `*_starter` | 视情况 | 业务模块只引 starter，零侵入 |
| **业务** | `auth/user/...` | starter + 业务特定依赖 | 各自独立可执行 jar |

**禁止**：
- ❌ 业务模块直接引用 `mybatis-plus-spring-boot3-starter`，必须通过 `ai-platform-mybatis-starter`
- ❌ 业务模块自己写 `MybatisPlusInterceptor` 注册
- ❌ 跨业务模块互相 `@ComponentScan`（用 Feign 替代）

## 2. Bean 注入规范

### 强制
- ✅ 字段注入用 `@Autowired`（byType）或 `@Resource`（byName）
- ✅ 多个同类 Bean 时用 `@Qualifier("name")`
- ✅ Optional 依赖用 `Optional<BeanType> field` 或在 setter 中 `if (jwtUtils != null)`
- ✅ Bean 字段 **不加 final**（除非使用 Lombok `@RequiredArgsConstructor` 且通过构造器注入，并且确认无循环依赖）

### 禁止
- ❌ `private final SomeBean` + `@Autowired` 字段（混用模式）
- ❌ Static 字段持有 Bean 引用
- ❌ 业务方法里 `new SomeBean()` 重复创建

### 示例

```java
// GOOD
@Service
public class AuthService {
    @Resource
    private UserServiceClient userClient;   // byName
    
    @Autowired
    private JwtUtils jwtUtils;              // byType
}

// GOOD - Optional 依赖
@Component
public class TenantInterceptor {
    @Autowired(required = false)
    private JwtUtils jwtUtils;              // 允许 null
    
    private Long resolveTenantId(...) {
        if (jwtUtils == null) return null;
        // ...
    }
}
```

## 3. Maven 依赖规则

### 父 POM 统一管理
- 所有版本号集中在父 POM `<properties>` + `<dependencyManagement>`
- Aliyun mirror 优先，Central 兜底
- 不在子 POM 中硬编码 `<version>`（除 optional 依赖）

### 业务模块只引 starter
```xml
<dependencies>
    <dependency>
        <groupId>com.aiplatform</groupId>
        <artifactId>ai-platform-web-starter</artifactId>
    </dependency>
    <dependency>
        <groupId>com.aiplatform</groupId>
        <artifactId>ai-platform-mybatis-starter</artifactId>
    </dependency>
    <!-- 业务特定依赖 (如 mysql-connector) -->
    <dependency>
        <groupId>com.mysql</groupId>
        <artifactId>mysql-connector-j</artifactId>
    </dependency>
</dependencies>
```

## 4. Nacos 可选装配

### application.yml
```yaml
spring:
  cloud:
    nacos:
      discovery:
        enabled: ${NACOS_DISCOVERY_ENABLED:false}   # 默认关
        server-addr: ${NACOS_SERVER:127.0.0.1:8848}
      config:
        enabled: ${NACOS_CONFIG_ENABLED:false}
        import-check:
          enabled: false                            # 关键: 关强制校验
```

### nacos-starter
```java
@Configuration
@ConditionalOnProperty(name = "aiplatform.nacos.enabled", havingValue = "true", matchIfMissing = false)
public class NacosAutoConfiguration { ... }
```

### 启用
```bash
NACOS_DISCOVERY_ENABLED=true NACOS_CONFIG_ENABLED=true \
NACOS_SERVER=192.168.1.10:8848 \
java -jar ai-platform-gateway.jar
```

## 5. 测试规范

- **Common**: JUnit 5 纯 Java 测试（无 Spring 上下文）
- **业务模块**: `@SpringBootTest` + H2 内存库（`@ActiveProfiles("test")`）
- **集成测试**: 暂时跳过（未来用 Testcontainers）

## 6. 启动顺序

由于 starter 解耦，没有强制启动顺序。但约定：

1. **Nacos / MySQL / Redis / ES**（中间件）
2. **Gateway**（9000）— 入口
3. **Auth**（9001）— 必须早于其他业务
4. **User**（9002）— auth 的依赖
5. **其他业务**（system / model / agent / knowledge / inference）

**Inference** 可独立运行，**不依赖其他服务**。

## 7. 故障排查

| 问题 | 排查 |
| --- | --- |
| `APPLICATION FAILED TO START: No spring.config.import property has been defined` | application.yml 加 `spring.cloud.nacos.config.import-check.enabled: false` |
| `Consider defining a bean of type 'JwtUtils'` | secure-starter 没扫到，确认 application 主类 `@ComponentScan` 包含 `com.aiplatform.starter.secure` |
| `Property 'sqlSessionFactory' or 'sqlSessionTemplate' are required` | 没引入 mybatis-starter，或 datasource 没配置（测试用 H2） |
| `UnsatisfiedDependency: JwtUtils` | inference 缺 `ai-platform-secure-starter`（在 inference pom 显式加） |
