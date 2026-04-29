package icu.sprinkle.loom.spring.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sprinkle-Loom 配置属性。
 *
 * @author sprinkle
 * @since 0.7.0 (MVP6)
 */
@ConfigurationProperties(prefix = "sprinkle-loom")
public class SprinkleLoomProperties {

    private Llm llm = new Llm();
    private Agent agent = new Agent();
    private Tools tools = new Tools();
    private Gateway gateway = new Gateway();
    private Mcp mcp = new Mcp();

    public Llm getLlm() {
        return llm;
    }

    public void setLlm(Llm llm) {
        this.llm = llm;
    }

    public Agent getAgent() {
        return agent;
    }

    public void setAgent(Agent agent) {
        this.agent = agent;
    }

    public Tools getTools() {
        return tools;
    }

    public void setTools(Tools tools) {
        this.tools = tools;
    }

    public Gateway getGateway() {
        return gateway;
    }

    public void setGateway(Gateway gateway) {
        this.gateway = gateway;
    }

    public Mcp getMcp() {
        return mcp;
    }

    public void setMcp(Mcp mcp) {
        this.mcp = mcp;
    }

    /**
     * 多实例 LLM 配置。<br>
     * 必填 instance ≥ 2 时 primary 必须显式指定；只有 1 个 instance 时该实例自动作为 primary。
     */
    public static class Llm {
        private String primary;
        private Map<String, Instance> instances = new LinkedHashMap<>();

        public String getPrimary() {
            return primary;
        }

        public void setPrimary(String primary) {
            this.primary = primary;
        }

        public Map<String, Instance> getInstances() {
            return instances;
        }

        public void setInstances(Map<String, Instance> instances) {
            this.instances = instances;
        }

        /**
         * 单个 LLM 实例配置。<br>
         * agent/tools 字段使用包装类型，{@code null} 表示沿用全局 {@link Agent} / {@link Tools} 默认值；
         * 非 {@code null} 时覆盖全局。
         */
        public static class Instance {
            // ===== LLM 字段 =====
            private String provider;
            private String apiKey;
            private String model;
            private String baseUrl;

            // ===== Agent 覆盖字段（null 沿用全局） =====
            private Integer maxIterations;
            private Duration loopTimeout;
            private String systemPrompt;
            private String workingDirectory;
            private Integer compactionThreshold;

            // ===== Tools 覆盖字段（null 沿用全局） =====
            private List<String> blockedCommands;

            public String getProvider() { return provider; }
            public void setProvider(String provider) { this.provider = provider; }
            public String getApiKey() { return apiKey; }
            public void setApiKey(String apiKey) { this.apiKey = apiKey; }
            public String getModel() { return model; }
            public void setModel(String model) { this.model = model; }
            public String getBaseUrl() { return baseUrl; }
            public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
            public Integer getMaxIterations() { return maxIterations; }
            public void setMaxIterations(Integer maxIterations) { this.maxIterations = maxIterations; }
            public Duration getLoopTimeout() { return loopTimeout; }
            public void setLoopTimeout(Duration loopTimeout) { this.loopTimeout = loopTimeout; }
            public String getSystemPrompt() { return systemPrompt; }
            public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }
            public String getWorkingDirectory() { return workingDirectory; }
            public void setWorkingDirectory(String workingDirectory) { this.workingDirectory = workingDirectory; }
            public Integer getCompactionThreshold() { return compactionThreshold; }
            public void setCompactionThreshold(Integer compactionThreshold) { this.compactionThreshold = compactionThreshold; }
            public List<String> getBlockedCommands() { return blockedCommands; }
            public void setBlockedCommands(List<String> blockedCommands) { this.blockedCommands = blockedCommands; }
        }
    }

    public static class Agent {
        private int maxIterations = 200;
        private Duration loopTimeout = Duration.ofMinutes(30);
        private String systemPrompt = "";
        private String workingDirectory;
        private int compactionThreshold = 100_000;

        public int getMaxIterations() {
            return maxIterations;
        }

        public void setMaxIterations(int maxIterations) {
            this.maxIterations = maxIterations;
        }

        public Duration getLoopTimeout() {
            return loopTimeout;
        }

        public void setLoopTimeout(Duration loopTimeout) {
            this.loopTimeout = loopTimeout;
        }

        public String getSystemPrompt() {
            return systemPrompt;
        }

        public void setSystemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
        }

        public String getWorkingDirectory() {
            return workingDirectory;
        }

        public void setWorkingDirectory(String workingDirectory) {
            this.workingDirectory = workingDirectory;
        }

        public int getCompactionThreshold() {
            return compactionThreshold;
        }

        public void setCompactionThreshold(int compactionThreshold) {
            this.compactionThreshold = compactionThreshold;
        }
    }

    public static class Tools {
        private boolean builtinEnabled = true;
        private List<String> blockedCommands = new ArrayList<>();

        public boolean isBuiltinEnabled() {
            return builtinEnabled;
        }

        public void setBuiltinEnabled(boolean builtinEnabled) {
            this.builtinEnabled = builtinEnabled;
        }

        public List<String> getBlockedCommands() {
            return blockedCommands;
        }

        public void setBlockedCommands(List<String> blockedCommands) {
            this.blockedCommands = blockedCommands;
        }
    }

    public static class Gateway {
        private boolean enabled = false;
        private Auth auth = new Auth();
        private RateLimit rateLimit = new RateLimit();
        private Acl acl = new Acl();
        private Audit audit = new Audit();
        private Security security = new Security();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Auth getAuth() {
            return auth;
        }

        public void setAuth(Auth auth) {
            this.auth = auth;
        }

        public RateLimit getRateLimit() {
            return rateLimit;
        }

        public void setRateLimit(RateLimit rateLimit) {
            this.rateLimit = rateLimit;
        }

        public Acl getAcl() {
            return acl;
        }

        public void setAcl(Acl acl) {
            this.acl = acl;
        }

        public Audit getAudit() {
            return audit;
        }

        public void setAudit(Audit audit) {
            this.audit = audit;
        }

        public Security getSecurity() {
            return security;
        }

        public void setSecurity(Security security) {
            this.security = security;
        }
    }

    public static class Auth {
        private String type = "api-key";
        private List<Key> keys = new ArrayList<>();

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public List<Key> getKeys() {
            return keys;
        }

        public void setKeys(List<Key> keys) {
            this.keys = keys;
        }

        public static class Key {
            private String key;
            private String tenant;
            private String userId;
            private String plan = "FREE";

            public String getKey() {
                return key;
            }

            public void setKey(String key) {
                this.key = key;
            }

            public String getTenant() {
                return tenant;
            }

            public void setTenant(String tenant) {
                this.tenant = tenant;
            }

            public String getUserId() {
                return userId;
            }

            public void setUserId(String userId) {
                this.userId = userId;
            }

            public String getPlan() {
                return plan;
            }

            public void setPlan(String plan) {
                this.plan = plan;
            }
        }
    }

    public static class RateLimit {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Acl {
        private String defaultPolicy = "allow";
        private List<String> ipBlacklist = new ArrayList<>();
        private List<String> ipWhitelist = new ArrayList<>();

        public String getDefaultPolicy() {
            return defaultPolicy;
        }

        public void setDefaultPolicy(String defaultPolicy) {
            this.defaultPolicy = defaultPolicy;
        }

        public List<String> getIpBlacklist() {
            return ipBlacklist;
        }

        public void setIpBlacklist(List<String> ipBlacklist) {
            this.ipBlacklist = ipBlacklist;
        }

        public List<String> getIpWhitelist() {
            return ipWhitelist;
        }

        public void setIpWhitelist(List<String> ipWhitelist) {
            this.ipWhitelist = ipWhitelist;
        }
    }

    public static class Audit {
        private boolean enabled = false;
        private String logDirectory = "./logs";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getLogDirectory() {
            return logDirectory;
        }

        public void setLogDirectory(String logDirectory) {
            this.logDirectory = logDirectory;
        }
    }

    public static class Mcp {
        private List<Server> servers = new ArrayList<>();

        public List<Server> getServers() {
            return servers;
        }

        public void setServers(List<Server> servers) {
            this.servers = servers;
        }

        public static class Server {
            private String id;
            private String transport = "STDIO";
            private String command;
            private List<String> args = new ArrayList<>();
            private Map<String, String> env = new HashMap<>();
            private String url;
            private Map<String, String> headers = new HashMap<>();
            private Duration requestTimeout = Duration.ofSeconds(30);

            public String getId() {
                return id;
            }

            public void setId(String id) {
                this.id = id;
            }

            public String getTransport() {
                return transport;
            }

            public void setTransport(String transport) {
                this.transport = transport;
            }

            public String getCommand() {
                return command;
            }

            public void setCommand(String command) {
                this.command = command;
            }

            public List<String> getArgs() {
                return args;
            }

            public void setArgs(List<String> args) {
                this.args = args;
            }

            public Map<String, String> getEnv() {
                return env;
            }

            public void setEnv(Map<String, String> env) {
                this.env = env;
            }

            public String getUrl() {
                return url;
            }

            public void setUrl(String url) {
                this.url = url;
            }

            public Map<String, String> getHeaders() {
                return headers;
            }

            public void setHeaders(Map<String, String> headers) {
                this.headers = headers;
            }

            public Duration getRequestTimeout() {
                return requestTimeout;
            }

            public void setRequestTimeout(Duration requestTimeout) {
                this.requestTimeout = requestTimeout;
            }
        }
    }

    public static class Security {
        private boolean promptInjectionGuardEnabled = false;
        private boolean outputValidatorEnabled = false;

        public boolean isPromptInjectionGuardEnabled() {
            return promptInjectionGuardEnabled;
        }

        public void setPromptInjectionGuardEnabled(boolean promptInjectionGuardEnabled) {
            this.promptInjectionGuardEnabled = promptInjectionGuardEnabled;
        }

        public boolean isOutputValidatorEnabled() {
            return outputValidatorEnabled;
        }

        public void setOutputValidatorEnabled(boolean outputValidatorEnabled) {
            this.outputValidatorEnabled = outputValidatorEnabled;
        }
    }
}