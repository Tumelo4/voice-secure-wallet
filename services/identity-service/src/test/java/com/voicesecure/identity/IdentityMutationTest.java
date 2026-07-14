package com.voicesecure.identity;

import org.junit.jupiter.api.Test;

final class IdentityMutationTest {
    @Test
    void exercisesJwtAndRefreshTokenSecurityBranches() throws Exception {
        JwtCodecTests.main(new String[0]);
        IdentityServiceTests.main(new String[0]);
        RefreshTokenRotationTests.main(new String[0]);
        DeviceSignatureVerifierTests.main(new String[0]);
    }
}
