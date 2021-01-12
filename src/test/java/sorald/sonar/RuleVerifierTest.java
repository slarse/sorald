package sorald.sonar;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;

import java.io.File;
import java.nio.file.Paths;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.sonar.check.Rule;
import org.sonar.plugins.java.api.JavaFileScanner;
import org.sonar.plugins.java.api.JavaFileScannerContext;
import sorald.Constants;

class RuleVerifierTest {

    @Test
    public void analyze_filtersOutMessages_thatLackPrimaryLocation() {
        String testFile =
                Paths.get(Constants.PATH_TO_RESOURCES_FOLDER)
                        .resolve("ArrayHashCodeAndToString.java")
                        .toString();
        var violations =
                RuleVerifier.analyze(
                        List.of(testFile),
                        new File(Constants.PATH_TO_RESOURCES_FOLDER),
                        new CheckWithNoLocation());

        assertThat(violations, is(empty()));
    }

    @Rule(key = "0000")
    @SuppressWarnings("UnstableApiUsage")
    private static class CheckWithNoLocation implements JavaFileScanner {
        @Override
        public void scanFile(JavaFileScannerContext context) {
            // setting the line to -1 causes the message to not have a primary location
            context.addIssue(-1, this, "This is a bogus message");
        }
    }
}