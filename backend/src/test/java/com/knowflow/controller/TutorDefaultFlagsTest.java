package com.knowflow.controller;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import com.knowflow.learning.tutor.TutorMicroService;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(properties={"knowflow.product.private-quiz.enabled=false","knowflow.product.tutor.enabled=false","knowflow.product.tutor-coaching.enabled=false"}) @ActiveProfiles("local")
class TutorDefaultFlagsTest {
 @Autowired ObjectProvider<TutorMicroService> micro;
 @Test void disabledProductExtensionsDoNotPreventOriginalApplicationStartup(){assertNull(micro.getIfAvailable());}
}
