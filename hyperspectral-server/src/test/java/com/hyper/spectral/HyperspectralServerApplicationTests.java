package com.hyper.spectral;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "detection.compatibility.default-algorithm=hcem",
        "detection.compatibility.random-delay-min-seconds=0",
        "detection.compatibility.random-delay-max-seconds=0"
})
class HyperspectralServerApplicationTests {

    @Test
    void contextLoads() {
    }
}
