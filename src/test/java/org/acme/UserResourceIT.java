package org.acme;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;

@QuarkusIntegrationTest

class UserResourceIT extends UserResourceTest {
    // Execute the same tests but in packaged mode.
}
