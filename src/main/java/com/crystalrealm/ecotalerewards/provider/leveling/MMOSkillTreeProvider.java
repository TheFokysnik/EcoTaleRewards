package com.crystalrealm.ecotalerewards.provider.leveling;

import com.crystalrealm.ecotalerewards.util.PluginLogger;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Level provider for <b>MMOSkillTree</b> by Ziggfreed.
 *
 * <p>MMOSkillTree uses ECS with {@code Store/Ref} parameters.
 * Player context is cached via {@link #onPlayerJoin} and used for XP granting.</p>
 */
public class MMOSkillTreeProvider implements LevelProvider {

    private static final PluginLogger LOGGER = PluginLogger.forEnclosingClass();
    private static final String API_CLASS = "com.ziggfreed.mmoskilltree.api.MMOSkillTreeAPI";
    private static final String SKILL_TYPE_CLASS = "com.ziggfreed.mmoskilltree.data.SkillType";

    private final ConcurrentHashMap<UUID, Object[]> playerContext = new ConcurrentHashMap<>();

    private boolean available;
    private Method addXpMethod;
    private Class<?> storeClass;
    private Class<?> refClass;
    private Class<?> skillTypeClass;
    private Object defaultSkillType;

    private String configSkillType;

    public MMOSkillTreeProvider(String configSkillType) {
        this.configSkillType = configSkillType;
        resolve();
    }

    private void resolve() {
        try {
            Class<?> apiClass = Class.forName(API_CLASS);
            skillTypeClass = Class.forName(SKILL_TYPE_CLASS);
            storeClass = Class.forName("com.hypixel.hytale.component.Store");
            refClass = Class.forName("com.hypixel.hytale.component.Ref");
            addXpMethod = apiClass.getMethod("addXp", storeClass, refClass, skillTypeClass, long.class);
            resolveDefaultSkillType();
            available = true;
            LOGGER.info("MMOSkillTreeAPI resolved successfully.");
        } catch (ClassNotFoundException e) {
            LOGGER.info("MMOSkillTree not found — provider disabled.");
            available = false;
        } catch (Exception e) {
            LOGGER.warn("Failed to resolve MMOSkillTreeAPI: {}", e.getMessage());
            available = false;
        }
    }

    private void resolveDefaultSkillType() {
        if (configSkillType == null || configSkillType.isEmpty()) {
            configSkillType = "SWORDS";
        }
        try {
            Method valueOf = skillTypeClass.getMethod("valueOf", String.class);
            defaultSkillType = valueOf.invoke(null, configSkillType.toUpperCase());
            LOGGER.info("MMOSkillTree: default skill type = {}", configSkillType.toUpperCase());
        } catch (Exception e) {
            LOGGER.warn("MMOSkillTree: invalid skill type '{}', falling back to SWORDS", configSkillType);
            try {
                Method valueOf = skillTypeClass.getMethod("valueOf", String.class);
                defaultSkillType = valueOf.invoke(null, "SWORDS");
            } catch (Exception ex) {
                LOGGER.error("MMOSkillTree: cannot resolve SWORDS: {}", ex.getMessage());
            }
        }
    }

    @Override
    public void onPlayerJoin(UUID uuid, Object store, Object ref) {
        playerContext.put(uuid, new Object[]{store, ref});
    }

    @Override
    public void onPlayerLeave(UUID uuid) {
        playerContext.remove(uuid);
    }

    @Override
    public String getName() {
        return "MMOSkillTree";
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public boolean grantXP(UUID playerUuid, double amount, String reason) {
        if (!isAvailable() || addXpMethod == null || defaultSkillType == null) return false;
        Object[] ctx = playerContext.get(playerUuid);
        if (ctx == null) {
            LOGGER.warn("MMOSkillTree: no cached context for {} — cannot grant XP", playerUuid);
            return false;
        }
        try {
            Object result = addXpMethod.invoke(null, ctx[0], ctx[1], defaultSkillType, (long) amount);
            return result instanceof Boolean b && b;
        } catch (Exception e) {
            LOGGER.warn("MMOSkillTree addXp failed for {} ({}): {}", playerUuid, amount, e.getMessage());
            return false;
        }
    }
}
