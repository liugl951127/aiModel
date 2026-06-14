package com.aiplatform.user;

import com.aiplatform.user.entity.SysUser;
import com.aiplatform.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        classes = {UserApplication.class}
)
@ActiveProfiles("test")
@ImportAutoConfiguration(DataSourceAutoConfiguration.class)
class UserServiceApplicationTests {

    @Autowired
    private UserService userService;

    @Test
    void contextLoads() {
        assertNotNull(userService);
    }

    @Test
    void createUserWithH2() {
        SysUser u = new SysUser();
        u.setUsername("testuser_" + System.currentTimeMillis());
        u.setPassword("hello");
        u.setNickname("Test");
        SysUser created = userService.create(u);
        assertNotNull(created.getId());
        assertTrue(created.getId() > 0);
    }
}
