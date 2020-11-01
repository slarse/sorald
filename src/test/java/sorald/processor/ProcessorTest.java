package sorald.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.sonar.plugins.java.api.JavaFileScanner;
import sorald.Constants;
import sorald.TestHelper;
import sorald.sonar.RuleVerifier;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtType;

public class ProcessorTest {

    /**
     * Parameterized test that processes a single Java file at a time with a single processor.
     *
     * <p>To add a new test for a rule with existing tests, add a new file to an existing test
     * directory in {@link ProcessorTestHelper#TEST_FILES_ROOT}. To add a new test for a rule
     * without existing tests, add a new directory+Java file in {@link
     * ProcessorTestHelper#TEST_FILES_ROOT} as described in the docs for {@link
     * ProcessorTestHelper#toProcessorTestCase(File)}.
     */
    @ParameterizedTest
    @ArgumentsSource(NonCompliantJavaFileProvider.class)
    public void testProcessSingleFile(
            ProcessorTestHelper.ProcessorTestCase<? extends JavaFileScanner> testCase)
            throws Exception {
        assertFalse(
                new File(Constants.SORALD_WORKSPACE).exists(),
                "workspace should must be clean before test");

        ProcessorTestHelper.runSorald(testCase);

        String pathToRepairedFile = testCase.repairedFilePath().toString();
        TestHelper.removeComplianceComments(pathToRepairedFile);
        RuleVerifier.verifyNoIssue(pathToRepairedFile, testCase.createCheckInstance());
    }

    /**
     * Provider class that provides test cases based on the buggy/non-compliant Java source files in
     * the test files directory.
     */
    private static class NonCompliantJavaFileProvider implements ArgumentsProvider {

        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext extensionContext) {
            return ProcessorTestHelper.getTestCaseStream().map(Arguments::of);
        }
    }

    /**
     * Parameterized test that processes a single Java file at a time with a single processor, and
     * compares the output to a reference. It executes on a subset of the test files acted upon by
     * {@link ProcessorTest#testProcessSingleFile(ProcessorTestHelper.ProcessorTestCase, File)}.
     *
     * <p>If a input test file A.java has a sibling A.java.expected, then this test is executed with
     * A.java.expected as the expected output from processing A.java.
     */
    @ParameterizedTest
    @ArgumentsSource(NonCompliantJavaFileWithExpectedProvider.class)
    public void testProcessSingleFile(
            ProcessorTestHelper.ProcessorTestCase<? extends JavaFileScanner> testCase,
            @TempDir File tempdir)
            throws Exception {
        // arrange
        assertFalse(
                new File(Constants.SORALD_WORKSPACE).exists(),
                "workspace should must be clean before test");

        // Spoon does not like parsing files that don't end in .java, so we must copy the .expected
        // files to end with .java
        Path expectedOutput = tempdir.toPath().resolve(testCase.nonCompliantFile.getName());
        Files.copy(
                testCase.expectedOutfile().orElseThrow(IllegalStateException::new).toPath(),
                expectedOutput);
        RuleVerifier.verifyNoIssue(
                expectedOutput.toAbsolutePath().toString(), testCase.createCheckInstance());

        // act
        ProcessorTestHelper.runSorald(testCase);

        // assert
        Path pathToRepairedFile = testCase.repairedFilePath();
        CtModel repairedModel = parseNoComments(pathToRepairedFile);
        CtModel expectedModel = parseNoComments(expectedOutput);

        List<CtType<?>> repairedTypes = getSortedTypes(repairedModel);
        List<CtType<?>> expectedTypes = getSortedTypes(expectedModel);
        List<CtImport> repairedImports = getSortedImports(repairedModel);
        List<CtImport> expectedImports = getSortedImports(expectedModel);

        assertEquals(expectedTypes, repairedTypes);
        assertEquals(repairedImports, expectedImports);
    }

    /**
     * Provider class that provides test cases based on the buggy/non-compliant Java source files in
     * the test files directory, that also have an expected outcome for the bugfix. The expected
     * files have the same name as their corresponding buggy files, but with the suffix ".expected".
     */
    private static class NonCompliantJavaFileWithExpectedProvider implements ArgumentsProvider {

        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext extensionContext) {
            return ProcessorTestHelper.getTestCaseStream()
                    .filter(testCase -> testCase.expectedOutfile().isPresent())
                    .map(Arguments::of);
        }
    }

    private static CtModel parseNoComments(Path javaFile) {
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(javaFile.toString());
        return launcher.buildModel();
    }

    private static List<CtImport> getSortedImports(CtModel model) {
        return model.getAllTypes().stream()
                .flatMap(
                        type ->
                                type
                                        .getFactory()
                                        .CompilationUnit()
                                        .getOrCreate(type)
                                        .getImports()
                                        .stream())
                .sorted(Comparator.comparing(CtImport::prettyprint))
                .collect(Collectors.toList());
    }

    private static List<CtType<?>> getSortedTypes(CtModel model) {
        return model.getAllTypes().stream()
                .sorted(Comparator.comparing(CtType::getQualifiedName))
                .collect(Collectors.toList());
    }
}