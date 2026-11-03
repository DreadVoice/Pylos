package com.dreadvoice.pylos.action;

import com.dreadvoice.pylos.paypal.InvoiceClient;
import com.dreadvoice.pylos.paypal.RefundClient;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.stereotype.Component;

import java.lang.reflect.Constructor;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class PayPalWriteAccessGuardTests {

    private static final String BASE_PACKAGE = "com.dreadvoice.pylos";
    private static final String PAYPAL_PACKAGE = "com.dreadvoice.pylos.paypal";

    @Test
    void actionExecutorIsTheOnlyComponentAllowedToHoldTheInvoiceClient() {
        Set<String> holders = componentsDependingOn(InvoiceClient.class);

        assertThat(holders).containsExactly(ActionExecutor.class.getName());
    }

    @Test
    void actionExecutorIsTheOnlyComponentAllowedToHoldTheRefundClient() {
        Set<String> holders = componentsDependingOn(RefundClient.class);

        assertThat(holders).containsExactly(ActionExecutor.class.getName());
    }

    @Test
    void theValidationPathCanReadFromPayPalButNeverReachAWriteClient() {
        for (Class<?> entryPoint : List.of(ActionValidator.class, RefundLimits.class)) {
            assertThat(reachableFrom(entryPoint))
                    .as("types reachable from %s", entryPoint.getSimpleName())
                    .doesNotContain(InvoiceClient.class, RefundClient.class);
        }
    }

    @Test
    void theCaptureLookupReadsWithoutHoldingAWriteClient() {
        assertThat(reachableFrom(com.dreadvoice.pylos.paypal.CaptureLookup.class))
                .doesNotContain(InvoiceClient.class, RefundClient.class)
                .contains(com.dreadvoice.pylos.paypal.CaptureReader.class);
    }

    @Test
    void neitherAgentToolCanReachAWriteClient() {
        for (Class<?> tool : List.of(com.dreadvoice.pylos.agent.ProposeTools.class,
                com.dreadvoice.pylos.agent.ReadTools.class)) {
            assertThat(constructorParameterTypes(tool))
                    .noneMatch(InvoiceClient.class::isAssignableFrom)
                    .noneMatch(RefundClient.class::isAssignableFrom);
        }
    }

    @Test
    void proposeToolsCannotReachThePayPalPackageAtAll() {
        assertThat(constructorParameterTypes(com.dreadvoice.pylos.agent.ProposeTools.class))
                .noneMatch(type -> type.getName().startsWith(PAYPAL_PACKAGE));
    }

    @Test
    void neitherTheValidatorNorTheServiceCanReachPayPalDirectly() {
        assertThat(constructorParameterTypes(ActionValidator.class))
                .noneMatch(type -> type.getName().startsWith(PAYPAL_PACKAGE));
        assertThat(constructorParameterTypes(ActionService.class))
                .noneMatch(type -> type.getName().startsWith(PAYPAL_PACKAGE));
    }

    private Set<String> componentsDependingOn(Class<?> writeClient) {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(Object.class));

        return scanner.findCandidateComponents(BASE_PACKAGE).stream()
                .map(definition -> definition.getBeanClassName())
                .filter(name -> name != null && !name.startsWith(PAYPAL_PACKAGE))
                .map(PayPalWriteAccessGuardTests::load)
                .filter(type -> type.isAnnotationPresent(Component.class)
                        || type.isAnnotationPresent(org.springframework.stereotype.Service.class))
                .filter(type -> constructorParameterTypes(type).stream()
                        .anyMatch(writeClient::isAssignableFrom))
                .map(Class::getName)
                .collect(Collectors.toCollection(java.util.TreeSet::new));
    }

    private static Set<Class<?>> reachableFrom(Class<?> entryPoint) {
        Set<Class<?>> seen = new java.util.LinkedHashSet<>();
        Deque<Class<?>> queue = new ArrayDeque<>(constructorParameterTypes(entryPoint));

        while (!queue.isEmpty()) {
            Class<?> next = queue.removeFirst();
            if (!next.getName().startsWith(BASE_PACKAGE) || !seen.add(next)) {
                continue;
            }
            queue.addAll(constructorParameterTypes(next));
        }

        return seen;
    }

    private static List<Class<?>> constructorParameterTypes(Class<?> type) {
        return Arrays.stream(type.getDeclaredConstructors())
                .map(Constructor::getParameterTypes)
                .flatMap(Arrays::stream)
                .collect(Collectors.toList());
    }

    private static Class<?> load(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
