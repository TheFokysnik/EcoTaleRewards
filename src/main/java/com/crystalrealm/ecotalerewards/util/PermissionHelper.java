package com.crystalrealm.ecotalerewards.util;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reads {@code permissions.json} and resolves group-based permissions manually,
 * because Hytale's native {@code hasPermission()} does NOT resolve groups.
 *
 * <p>Supports wildcard matching: {@code ecotalerewards.*} and {@code *}.</p>
 */
public final class PermissionHelper {

    private static final PluginLogger LOGGER = PluginLogger.forEnclosingClass();
    private static final Gson GSON = new Gson();

    private static volatile PermissionHelper instance;

    /** UUID → list of group names */
    private final Map<String, List<String>> userGroups = new ConcurrentHashMap<>();
    /** Group name → set of permissions */
    private final Map<String, Set<String>> groupPermissions = new ConcurrentHashMap<>();

    private Path permissionsFile;

    private PermissionHelper() {}

    // ── Singleton ───────────────────────────────────────────

    @Nonnull
    public static PermissionHelper getInstance() {
        if (instance == null) {
            synchronized (PermissionHelper.class) {
                if (instance == null) {
                    instance = new PermissionHelper();
                }
            }
        }
        return instance;
    }

    // ── Init / Reload ───────────────────────────────────────

    /**
     * Initialize the helper by locating and loading permissions.json.
     *
     * @param pluginDataDir the plugin's data directory (from getDataDirectory())
     */
    public void init(@Nonnull Path pluginDataDir) {
        permissionsFile = findPermissionsFile(pluginDataDir);
        if (permissionsFile != null) {
            load();
        } else {
            LOGGER.warn("permissions.json not found — group permission resolution disabled");
        }
    }

    /**
     * Reload permissions.json (e.g. on /reload command).
     */
    public void reload() {
        if (permissionsFile != null && Files.exists(permissionsFile)) {
            load();
        }
    }

    // ── LuckPerms API (reflection) ─────────────────────────

    private Object luckPermsApi;         // net.luckperms.api.LuckPerms
    private Object luckPermsUserManager; // net.luckperms.api.model.user.UserManager
    private boolean luckPermsAvailable = false;
    private boolean luckPermsChecked = false;

    /**
     * Try to initialize LuckPerms API access via reflection.
     * Called lazily on first permission check.
     */
    private void initLuckPerms() {
        if (luckPermsChecked) return;
        luckPermsChecked = true;
        try {
            Class<?> providerClass = Class.forName("net.luckperms.api.LuckPermsProvider");
            luckPermsApi = providerClass.getMethod("get").invoke(null);
            luckPermsUserManager = luckPermsApi.getClass().getMethod("getUserManager").invoke(luckPermsApi);
            luckPermsAvailable = true;
            LOGGER.info("LuckPerms API detected — using it for permission resolution");
        } catch (ClassNotFoundException e) {
            LOGGER.info("LuckPerms not found — using permissions.json only");
        } catch (Exception e) {
            LOGGER.warn("LuckPerms API init failed: {} — using permissions.json", e.getMessage());
        }
    }

    /**
     * Check a permission via LuckPerms API (reflection).
     * Returns null if LuckPerms is unavailable or the check fails.
     */
    @Nullable
    private Boolean checkLuckPerms(@Nonnull UUID uuid, @Nonnull String permission) {
        initLuckPerms();
        if (!luckPermsAvailable || luckPermsUserManager == null) return null;

        try {
            // UserManager.getUser(UUID) — returns User or null (only if loaded)
            Object user = luckPermsUserManager.getClass()
                    .getMethod("getUser", UUID.class)
                    .invoke(luckPermsUserManager, uuid);

            if (user == null) {
                // User not loaded — try loadUser(UUID).join()
                Object future = luckPermsUserManager.getClass()
                        .getMethod("loadUser", UUID.class)
                        .invoke(luckPermsUserManager, uuid);
                user = future.getClass().getMethod("join").invoke(future);
            }

            if (user == null) return null;

            // User.getCachedData() → CachedDataManager
            Object cachedData = user.getClass().getMethod("getCachedData").invoke(user);
            // CachedDataManager.getPermissionData() → CachedPermissionData
            Object permData = cachedData.getClass().getMethod("getPermissionData").invoke(cachedData);
            // CachedPermissionData.checkPermission(String) → TriState
            Object triState = permData.getClass().getMethod("checkPermission", String.class)
                    .invoke(permData, permission);
            // TriState.asBoolean() → boolean
            return (Boolean) triState.getClass().getMethod("asBoolean").invoke(triState);
        } catch (Exception e) {
            LOGGER.debug("LuckPerms permission check failed for {}: {}", uuid, e.getMessage());
            return null;
        }
    }

    // ── Permission check ────────────────────────────────────

    /**
     * Check if a player (by UUID) has the given permission,
     * resolving through LuckPerms API first, then falling back
     * to groups and wildcard matching from permissions.json.
     *
     * @param uuid       player UUID
     * @param permission permission node to check
     * @return true if the player has this permission
     */
    public boolean hasPermission(@Nonnull UUID uuid, @Nonnull String permission) {
        // 1. Try LuckPerms API first
        Boolean lpResult = checkLuckPerms(uuid, permission);
        if (lpResult != null) return lpResult;

        // 2. Fallback: permissions.json
        String uuidStr = uuid.toString();
        List<String> groups = userGroups.get(uuidStr);
        if (groups == null || groups.isEmpty()) {
            // Check Default group as fallback
            Set<String> defaultPerms = groupPermissions.get("Default");
            if (defaultPerms != null) {
                return matchesAny(defaultPerms, permission);
            }
            return false;
        }

        for (String group : groups) {
            Set<String> perms = groupPermissions.get(group);
            if (perms != null && matchesAny(perms, permission)) {
                return true;
            }
        }
        return false;
    }

    // ── Wildcard matching ───────────────────────────────────

    /**
     * Check if the requested permission matches any entry in the set.
     * Handles wildcards like {@code ecotalerewards.*} and {@code *}.
     */
    private boolean matchesAny(@Nonnull Set<String> grantedPerms, @Nonnull String requested) {
        // 1. Exact match
        if (grantedPerms.contains(requested)) return true;

        // 2. Global wildcard
        if (grantedPerms.contains("*")) return true;

        // 3. Check each wildcard entry in the granted set
        //    e.g. "ecotalerewards.*" should match "ecotalerewards.use"
        for (String granted : grantedPerms) {
            if (granted.endsWith(".*")) {
                String prefix = granted.substring(0, granted.length() - 2);
                if (requested.startsWith(prefix + ".") || requested.equals(prefix)) {
                    return true;
                }
            }
        }

        return false;
    }

    // ── File loading ────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void load() {
        userGroups.clear();
        groupPermissions.clear();

        try (Reader reader = new InputStreamReader(
                Files.newInputStream(permissionsFile), StandardCharsets.UTF_8)) {

            Type mapType = new TypeToken<Map<String, Object>>() {}.getType();
            Map<String, Object> root = GSON.fromJson(reader, mapType);

            // Parse "users"
            Map<String, Object> users = (Map<String, Object>) root.get("users");
            if (users != null) {
                for (Map.Entry<String, Object> entry : users.entrySet()) {
                    String uuid = entry.getKey();
                    Map<String, Object> userData = (Map<String, Object>) entry.getValue();
                    List<String> groups = (List<String>) userData.get("groups");
                    if (groups != null) {
                        userGroups.put(uuid, new ArrayList<>(groups));
                    }
                }
            }

            // Parse "groups"
            Map<String, Object> groups = (Map<String, Object>) root.get("groups");
            if (groups != null) {
                for (Map.Entry<String, Object> entry : groups.entrySet()) {
                    String groupName = entry.getKey();
                    List<String> perms = (List<String>) entry.getValue();
                    if (perms != null) {
                        Set<String> permSet = new HashSet<>();
                        for (String p : perms) {
                            if (p != null && !p.startsWith("#")) {
                                permSet.add(p.trim());
                            }
                        }
                        groupPermissions.put(groupName, permSet);
                    }
                }
            }

            LOGGER.info("permissions.json loaded: {} users, {} groups",
                    userGroups.size(), groupPermissions.size());

        } catch (IOException e) {
            LOGGER.error("Failed to load permissions.json: {}", e.getMessage());
        } catch (Exception e) {
            LOGGER.error("Error parsing permissions.json: {}", e.getMessage());
        }
    }

    @Nullable
    private Path findPermissionsFile(@Nonnull Path pluginDataDir) {
        // Try multiple locations
        Path[] candidates = {
                Paths.get("permissions.json"),                         // CWD (server root)
                pluginDataDir.resolve("../permissions.json"),          // parent of plugin data
                pluginDataDir.resolve("../../permissions.json"),       // two levels up
                pluginDataDir.getParent() != null
                        ? pluginDataDir.getParent().resolve("permissions.json")
                        : null
        };

        for (Path candidate : candidates) {
            if (candidate != null) {
                Path normalized = candidate.toAbsolutePath().normalize();
                if (Files.exists(normalized)) {
                    LOGGER.info("Found permissions.json at: {}", normalized);
                    return normalized;
                }
            }
        }

        // Last resort: walk up from plugin dir
        Path current = pluginDataDir.toAbsolutePath().normalize();
        for (int i = 0; i < 5; i++) {
            Path parent = current.getParent();
            if (parent == null) break;
            Path test = parent.resolve("permissions.json");
            if (Files.exists(test)) {
                LOGGER.info("Found permissions.json at: {}", test);
                return test;
            }
            current = parent;
        }

        return null;
    }
}
