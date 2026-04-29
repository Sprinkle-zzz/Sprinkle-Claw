package icu.sprinkle.loom.spring.autoconfigure;

import icu.sprinkle.loom.bootstrap.Loom;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;

/**
 * 根据 {@link SprinkleLoomProperties#getLlm()} 中的 {@code instances} Map 动态注册命名 {@link Loom} bean。
 *
 * <p>每个 instance 注册一个 {@link RootBeanDefinition}，通过 {@code factoryBeanName=sprinkleLoomFactory}
 * + {@code factoryMethodName=create} + 构造参数 instance 名引用 {@link SprinkleLoomFactory#create(String)}
 * 实际构建 Claw。所有 BeanDefinition 设置 {@code destroyMethodName=close} 确保 Spring 容器关闭时
 * 正确释放 MCP 连接等资源。</p>
 *
 * <p><b>Primary 选择规则</b>：</p>
 * <ul>
 *   <li>显式 {@code primary} 字段：必须指向已存在的 instance 名，否则启动报错</li>
 *   <li>仅 1 个 instance：该实例自动作为 primary</li>
 *   <li>≥ 2 个 instance 且未指定 primary：启动报错</li>
 *   <li>0 个 instance：不注册任何 Loom bean（{@code @Autowired Loom} 将抛 NoSuchBean）</li>
 * </ul>
 *
 * @author sprinkle
 * @since 0.10.0 (MVP9)
 */
public class SprinkleLoomBeanRegistrar implements BeanDefinitionRegistryPostProcessor, EnvironmentAware {

    static final String FACTORY_BEAN_NAME = "sprinkleLoomFactory";

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        SprinkleLoomProperties props = Binder.get(environment)
                .bind("sprinkle-loom", SprinkleLoomProperties.class)
                .orElseGet(SprinkleLoomProperties::new);

        SprinkleLoomProperties.Llm llm = props.getLlm();
        if (llm.getInstances() == null || llm.getInstances().isEmpty()) {
            return;
        }

        String primaryName = resolvePrimaryName(llm);

        for (String name : llm.getInstances().keySet()) {
            RootBeanDefinition bd = new RootBeanDefinition(Loom.class);
            bd.setFactoryBeanName(FACTORY_BEAN_NAME);
            bd.setFactoryMethodName("create");
            bd.getConstructorArgumentValues().addGenericArgumentValue(name);
            bd.setDestroyMethodName("close");
            if (name.equals(primaryName)) {
                bd.setPrimary(true);
            }
            registry.registerBeanDefinition(name, bd);
        }
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        // 无需后处理，所有 Bean 在 postProcessBeanDefinitionRegistry 中注册完毕
    }

    private static String resolvePrimaryName(SprinkleLoomProperties.Llm llm) {
        String configured = llm.getPrimary();
        if (configured != null && !configured.isEmpty()) {
            if (!llm.getInstances().containsKey(configured)) {
                throw new IllegalStateException(
                        "sprinkle-loom.llm.primary='" + configured
                                + "' does not match any configured instance. Available: "
                                + llm.getInstances().keySet());
            }
            return configured;
        }
        if (llm.getInstances().size() == 1) {
            return llm.getInstances().keySet().iterator().next();
        }
        throw new IllegalStateException(
                "sprinkle-loom.llm.primary must be explicitly set when 2+ instances are configured. "
                        + "Configured instances: " + llm.getInstances().keySet());
    }
}
