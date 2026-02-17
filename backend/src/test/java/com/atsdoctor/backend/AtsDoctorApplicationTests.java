package com.atsdoctor.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "ats.doctor.persistence.enabled=false")
class AtsDoctorApplicationTests {

    @Test
    void contextLoads() {
    }
}
