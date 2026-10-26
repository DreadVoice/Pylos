package com.dreadvoice.pylos.action;

import com.dreadvoice.pylos.paypal.InvoiceClient;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.stereotype.Component;

import java.lang.reflect.Constructor;
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
        Set<String> holders = componentsDependingOnInvoiceClient();

        assertThat(holders).containsExactly(ActionExecutor.class.getName());
    }

    @Test
    void neitherTheValidatorNorTheServiceCanReachPayPalDirectly() {
        assertThat(constructorParameterTypes(ActionValidator.class))
                .noneMatch(type -> type.getName().startsWith(PAYPAL_PACKAGE));
        assertThat(constructorParameterTypes(ActionService.class))
                .noneMatch(type -> type.getName().startsWith(PAYPAL_PACKAGE));
    }

    private Set<String> componentsDependingOnInvoiceClient() {
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
                        .anyMatch(InvoiceClient.class::isAssignableFrom))
                .map(Class::getName)
                .collect(Collectors.toCollection(java.util.TreeSet::new));
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
