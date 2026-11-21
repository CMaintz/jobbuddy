package com.autoapplicant.adapter;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * The chat's agent and JobBuddy's MCP server meet only through a port. Reaching into the MCP
 * adapter from the agent would let the process that reads untrusted posting text grow a direct
 * line into the server's internals.
 */
@AnalyzeClasses(packages = "com.autoapplicant", importOptions = ImportOption.DoNotIncludeTests.class)
class ChatAdapterBoundaryTest {

    @ArchTest
    static final ArchRule agent_does_not_depend_on_the_mcp_server = noClasses()
            .that().resideInAPackage("com.autoapplicant.adapter.ai..")
            .should().dependOnClassesThat().resideInAPackage("com.autoapplicant.adapter.mcp..")
            .because("the agent reaches tools through port.out.chat.ChatToolsPort");
}
