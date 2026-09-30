package com.fluxcraft.MiaoMenu.bedrockmenu;

import java.io.File;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import com.fluxcraft.MiaoMenu.MiaoMenu;
import com.fluxcraft.MiaoMenu.foliacall.FoliaFactory;
import com.fluxcraft.MiaoMenu.managers.SoundsClock;
import com.fluxcraft.MiaoMenu.menu.action.ActionRegistry;
import com.fluxcraft.MiaoMenu.menu.requirement.RequirementFeedbackHandler;
import com.fluxcraft.MiaoMenu.menu.requirement.RequirementResult;
import com.fluxcraft.MiaoMenu.menu.requirement.RequirementService;
import com.fluxcraft.MiaoMenu.utils.Lang;
import com.fluxcraft.MiaoMenu.utils.MenuUtils;
import com.fluxcraft.MiaoMenu.utils.PlaceholderUtils;

public class BedrockMenuManager {
    private final MiaoMenu plugin;
    private final ActionRegistry actionRegistry;
    private final SoundsClock soundsClock;
    private final RequirementService requirementService;
    private final RequirementFeedbackHandler requirementFeedbackHandler;
    private volatile FloodgateReflectionAccess reflectionAccess;
    private volatile Map<String, BedrockMenu> menus = Collections.emptyMap();

    public BedrockMenuManager(
            MiaoMenu plugin,
            ActionRegistry actionRegistry,
            SoundsClock soundsClock,
            RequirementService requirementService,
            RequirementFeedbackHandler requirementFeedbackHandler
    ) {
        this(plugin, actionRegistry, soundsClock, requirementService, requirementFeedbackHandler, true);
    }

    public BedrockMenuManager(
            MiaoMenu plugin,
            ActionRegistry actionRegistry,
            SoundsClock soundsClock,
            RequirementService requirementService,
            RequirementFeedbackHandler requirementFeedbackHandler,
            boolean floodgateAvailable
    ) {
        this.plugin = plugin;
        this.actionRegistry = actionRegistry;
        this.soundsClock = soundsClock;
        this.requirementService = requirementService;
        this.requirementFeedbackHandler = requirementFeedbackHandler;
        this.reflectionAccess = floodgateAvailable ? createReflectionAccess() : null;
    }

    private FloodgateReflectionAccess createReflectionAccess() {
        try {
            return new FloodgateReflectionAccess();
        } catch (RuntimeException | LinkageError e) {
            plugin.getLogger().log(Level.WARNING, Lang.get("log.bedrock-menu.reflection-setup-failed"), e);
            return null;
        }
    }

    public boolean isEnabled() {
        return reflectionAccess != null;
    }

    public void loadAllMenus() {
        if (!isEnabled()) {
            menus = Collections.emptyMap();
            return;
        }
        Map<String, BedrockMenu> newMenus = new ConcurrentHashMap<>();
        File dir = new File(plugin.getDataFolder(), "bedrock_menus");
        if (!dir.exists() && !dir.mkdirs()) {
            plugin.getLogger().severe(Lang.get("log.bedrock-menu.directory-create-failed"));
            return;
        }
        File[] files = dir.listFiles((_, n) -> n.endsWith(".yml"));
        if (files == null) {
            return;
        }
        for (File file : files) {
            try {
                String name = file.getName().replace(".yml", "");
                FileConfiguration config = YamlConfiguration.loadConfiguration(file);
                newMenus.put(name, new BedrockMenu(name, config, plugin, requirementService));
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, Lang.get("log.bedrock-menu.load-failed").replace("{0}", file.getName()), e);
            }
        }
        menus = newMenus;
    }

    public void openMenu(Player player, String menuName) {
        if (!isEnabled()) {
            player.sendMessage(Lang.get("open.error"));
            return;
        }
        BedrockMenu menu = menus.get(menuName);
        if (MenuUtils.handleMenuNotFound(player, menu, menuName)) {
            return;
        }
        RequirementResult requirementResult = menu.checkViewRequirement(player);
        if (MenuUtils.handleRequirementDenied(requirementFeedbackHandler, player, requirementResult)) {
            return;
        }
        soundsClock.playMenuOpenSound(player);
        try {
            sendFloodgateForm(player, menu);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, Lang.get("log.bedrock-menu.send-failed")
                    .replace("{0}", menuName)
                    .replace("{1}", player.getName()), e);
            player.sendMessage(Lang.get("open.error"));
        }
    }

    private void sendFloodgateForm(Player player, BedrockMenu menu) {
        FloodgateReflectionAccess access = reflectionAccess;
        if (access == null) {
            throw new IllegalStateException(Lang.get("log.bedrock-menu.reflection-setup-failed"));
        }
        Object formBuilder = menu.buildForm(player);
        if (formBuilder == null) {
            plugin.getLogger().warning(Lang.get("log.bedrock-menu.form-build-returned-null").replace("{0}", player.getName()));
            return;
        }
        try {
            Object builtForm = access.buildForm(formBuilder, createFormResponseHandler(menu.getAllItems(), player, menu));
            access.sendForm(player.getUniqueId(), builtForm);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(Lang.get("log.bedrock-menu.reflection-send-failed")
                    .replace("{0}", menu.getName())
                    .replace("{1}", player.getName()), e);
        }
    }

    private Consumer<Object> createFormResponseHandler(
            List<BedrockMenu.BedrockMenuItem> allItems,
            Player player,
            BedrockMenu menu
    ) {
        // Floodgate 表單回呼由 Floodgate 內部 Netty 緒觸發，並非主執行緒；
        // handleFormResponse 內會 sendMessage / 派發動作 / 開新選單，
        // 在 Folia 上若不排到該玩家所屬的 region 緒，會丟 IllegalStateException。
        // 統一在這層排程，下游的所有 player API 呼叫就一律安全。
        return response -> FoliaFactory.getAdapter().runForEntity(plugin, player, () -> {
            // 玩家在表單開啟到提交之間離線：Bukkit 主緒分支仍會排到下一 tick 跑，但對離線玩家 sendMessage 在新版 Paper 會丟例外，
            // 統一在進入點檔下；Folia 端對離線實體會 silent drop，這裡多一道 guard 也無壞處。
            if (!player.isOnline()) {
                return;
            }
            try {
                handleFormResponse(response, allItems, player, menu);
            } catch (ReflectiveOperationException e) {
                plugin.getLogger().log(Level.WARNING, Lang.get("log.bedrock-menu.reflection-send-failed")
                        .replace("{0}", menu.getName())
                        .replace("{1}", player.getName()), e);
            }
        });
    }

    private void handleFormResponse(
            Object response,
            List<BedrockMenu.BedrockMenuItem> allItems,
            Player player,
            BedrockMenu menu
    ) throws ReflectiveOperationException {
        // 反射回傳值可能為 null（玩家關閉表單）或非預期型別（Floodgate API 變動），避免拆箱 NPE。
        Object rawClicked = response.getClass().getMethod("clickedButtonId").invoke(response);
        if (!(rawClicked instanceof Integer)) {
            return;
        }
        int clickedIndex = (Integer) rawClicked;
        if (clickedIndex < 0 || clickedIndex >= allItems.size()) {
            return;
        }
        BedrockMenu.BedrockMenuItem item = allItems.get(clickedIndex);
        RequirementResult requirementResult = item.evaluateRequirement(
                player,
                requirementService,
                menu.getName(),
                menu.getRequirementBlocks()
        );
        if (!requirementResult.allowed()) {
            player.sendMessage(item.getLockMessage(player, plugin, requirementResult));
            return;
        }
        String cmd = item.getCommand();
        if (cmd == null || cmd.isEmpty()) {
            // 來源是 shift/middle/right-only 的按鈕（由 DeluxeMenus 匯入時自動標記），
            // 基岩 SimpleForm 一顆按鈕只能跑單一動作 → 點下去要明確提示玩家「這個動作 Java 版才有」
            if (item.unsupportedOnBedrock()) {
                player.sendMessage(Lang.get("bedrock.unsupported-button"));
            }
            return;
        }
        String parsed = PlaceholderUtils.parse(player, cmd, plugin);
        String executeAs = item.getExecuteAs();
        if (executeAs == null || executeAs.isBlank()) {
            actionRegistry.dispatch(player, parsed);
            return;
        }
        actionRegistry.dispatch(player, "[" + executeAs + "] " + parsed);
    }

    public Map<String, BedrockMenu> getMenus() {
        return Collections.unmodifiableMap(menus);
    }

    public ActionRegistry getActionRegistry() {
        return actionRegistry;
    }

    private static final class FloodgateReflectionAccess {
        private final Object floodgateApi;
        private final Method sendFormMethod;

        private FloodgateReflectionAccess() {
            try {
                Class<?> floodgateApiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
                floodgateApi = floodgateApiClass.getMethod("getInstance").invoke(null);
                sendFormMethod = findSendFormMethod(floodgateApiClass);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(Lang.get("log.bedrock-menu.reflection-setup-failed"), e);
            }
        }

        private Object buildForm(Object formBuilder, Consumer<Object> handler) throws ReflectiveOperationException {
            Method validResultHandlerMethod = formBuilder.getClass().getMethod("validResultHandler", Consumer.class);
            Method buildMethod = formBuilder.getClass().getMethod("build");
            validResultHandlerMethod.invoke(formBuilder, handler);
            return buildMethod.invoke(formBuilder);
        }

        private void sendForm(UUID uuid, Object form) throws ReflectiveOperationException {
            sendFormMethod.invoke(floodgateApi, uuid, form);
        }

        private Method findSendFormMethod(Class<?> floodgateApiClass) {
            // FloodgateApi 提供兩個 overload：sendForm(UUID, Form) 與 sendForm(UUID, FormBuilder<?,?,?>)。
            // 我們呼叫端傳的是已 build 完的 Form（SimpleForm 實例），必須鎖定第一個 overload，
            // 否則 getMethods() 的順序不保證，可能命中 FormBuilder 簽名導致 argument type mismatch。
            Method fallback = null;
            for (Method method : floodgateApiClass.getMethods()) {
                if (!method.getName().equals("sendForm")) continue;
                if (method.getParameterCount() != 2) continue;
                if (method.getParameterTypes()[0] != UUID.class) continue;
                String secondType = method.getParameterTypes()[1].getName();
                if (secondType.equals("org.geysermc.cumulus.form.Form")) {
                    return method;
                }
                fallback = method;
            }
            if (fallback != null) {
                return fallback;
            }
            throw new IllegalStateException(Lang.get("log.bedrock-menu.reflection-setup-failed"));
        }
    }
}
