package com.knowflow.learning.actions;
import java.time.Instant;
import org.springframework.stereotype.Component;
@Component public class PlanClock {public Instant now(){return Instant.now();}}
