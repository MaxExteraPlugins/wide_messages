import base64
import zlib

from base_plugin import BasePlugin
from java import jclass
from org.telegram.messenger import ApplicationLoader
from ui.settings import Header, Switch, Text, Divider

__id__ = "wide_messages"
__name__ = "Wide Messages"
__description__ = "Attempts to expand messages to fill the entire chat."
__author__ = "@itNotMax - @MaxExteraPlugins"
__version__ = "2.2.3"
__icon__ = "MaxExteraPluginsStickers/6"
__min_version__ = "12.10.6"
__app_version__ = ">=12.5.1"
__sdk_version__ = ">=1.4.3.3"

ENTRY_CLASS = "com.extera.plugins.widemessages.Main"
DEX_BEGIN = "__DEX_BEGIN__"
DEX_END = "__DEX_END__"
BuildVersion = jclass("android.os.Build$VERSION")
MIN_CHAR_THRESHOLD = 30


class Dex:
    def __init__(self, entry_class):
        self.entry_class = entry_class
        self.clazz = None
        self.loaded = False

    def load(self):
        if self.loaded:
            return
        if self.clazz is None:
            ctx = ApplicationLoader.applicationContext
            if ctx is None:
                raise RuntimeError("application context is not ready")
            if BuildVersion.SDK_INT < 26:
                raise RuntimeError("embedded DEX requires Android API 26+")
            ByteBuffer = jclass("java.nio.ByteBuffer")
            InMemoryDexClassLoader = jclass("dalvik.system.InMemoryDexClassLoader")
            loader = InMemoryDexClassLoader(ByteBuffer.wrap(self._read_payload()), ctx.getClassLoader())
            self.clazz = loader.loadClass(self.entry_class)
        try:
            self._invoke("load")
            self.loaded = True
        except Exception:
            try:
                self._invoke("unload")
            except Exception:
                pass
            self.loaded = False
            raise

    def unload(self):
        try:
            if self.clazz is not None and self.loaded:
                self._invoke("unload")
        except Exception:
            pass
        self.loaded = False

    def call(self, method_name, *args):
        if self.clazz is None or (method_name not in ("load", "unload") and not self.loaded):
            self.load()
        return self._invoke(method_name, *args)

    def _invoke(self, method_name, *args):
        if self.clazz is None:
            raise RuntimeError("embedded dex class is not loaded")
        
        for method in self.clazz.getDeclaredMethods():
            if str(method.getName()) != method_name:
                continue
            method_params = method.getParameterTypes()
            if len(method_params) != len(args):
                continue
            method.setAccessible(True)
            try:
                if len(args) == 0:
                    return method.invoke(None)
                else:
                    java_args = []
                    for i, arg in enumerate(args):
                        if isinstance(arg, bool):
                            java_args.append(jclass("java.lang.Boolean")(arg))
                        elif isinstance(arg, int):
                            java_args.append(jclass("java.lang.Integer")(arg))
                        else:
                            java_args.append(arg)
                    return method.invoke(None, java_args)
            except Exception as exc:
                cause = None
                try:
                    cause = exc.getCause()
                except Exception:
                    pass
                if cause is not None:
                    try:
                        message = cause.getMessage()
                    except Exception:
                        message = None
                    raise RuntimeError(str(message or cause))
                raise
        raise RuntimeError(f"core method not found: {method_name}")

    def _read_payload(self):
        try:
            with open(__file__, "r", encoding="utf-8") as source:
                text = source.read()
            block = text.split(f"# {DEX_BEGIN}", 1)[1].split(f"# {DEX_END}", 1)[0]
            payload = "".join(line.strip()[1:].strip() for line in block.splitlines() if line.strip().startswith("#"))
        except Exception as exc:
            raise RuntimeError(f"embedded dex read failed: {exc}")

        if not payload:
            raise RuntimeError("embedded dex payload not found")

        try:
            return zlib.decompress(base64.b64decode(payload))
        except Exception as exc:
            raise RuntimeError(f"embedded dex decode failed: {exc}")



# ============================================================
# MaxExteraPlugins Shared Author & Channel Subscription Module
# ============================================================

# ============================================================
# MaxExteraPlugins Shared Author & Channel Subscription Module
# ============================================================

# ============================================================
# MaxExteraPlugins Shared Author & Channel Subscription Module
# ============================================================
class AuthorChannelHelper:
    CONFIG_URL = "https://raw.githubusercontent.com/MaxExteraPlugins/config/main/links.json"

    DEFAULT_LINKS = {
        "channel_username": "MaxExteraPlugins",
        "channel_url": "https://t.me/MaxExteraPlugins",
        "header": {"ru": "MaxExteraPlugins", "en": "MaxExteraPlugins"},
        "buttons": [
            {"title": {"ru": "Канал автора", "en": "Author's Channel"}, "icon": "msg_channel", "url": "https://t.me/MaxExteraPlugins"},
            {"title": {"ru": "Поддержать автора", "en": "Support author"}, "icon": "msg_share", "url": "https://t.me/itNotMax"},
            {"title": {"ru": "Сообщить о баге", "en": "Report a bug"}, "icon": "msg_help", "url": "https://t.me/itNotMax"}
        ]
    }

    _TEXTS = {
        "install_card_title": {"ru": "Подпишитесь на канал", "en": "Subscribe to channel"},
        "install_card_msg": {
            "ru": (
                "Мы делаем плагины бесплатными и доступными для всех: пожалуйста, "
                "поддержите нас подпиской на официальный канал @MaxExteraPlugins.\n\n"
                "Там публикуются обновления, важные новости и анонсы новых плагинов."
            ),
            "en": (
                "We keep our plugins free and accessible for everyone: please "
                "support us by subscribing to our official channel @MaxExteraPlugins.\n\n"
                "Updates, news, and new plugin announcements are published there."
            )
        },
        "open_channel_btn": {"ru": "Перейти на канал", "en": "Open channel"},
        "remind_later_btn": {"ru": "Напомнить позже", "en": "Remind later"},
        "never_show_btn": {"ru": "Больше не показывать", "en": "Don't show again"},
        "sub_thank_you": {"ru": "Спасибо за подписку на наш канал!", "en": "Thank you for subscribing to our channel!"},
        "settings_channel": {"ru": "Канал автора", "en": "Author's Channel"},
        "settings_donate": {"ru": "Поддержать автора", "en": "Support author"},
        "settings_bug_report": {"ru": "Сообщить о баге", "en": "Report a bug"}
    }

    @classmethod
    def is_ru(cls) -> bool:
        try:
            from org.telegram.messenger import LocaleController
            lang = str(LocaleController.getInstance().getCurrentLocale().getLanguage() or "").lower()
            return lang in ("ru", "be", "uk", "kk")
        except Exception:
            return True

    @classmethod
    def tr(cls, key: str) -> str:
        entry = cls._TEXTS.get(key, {})
        return entry.get("ru" if cls.is_ru() else "en", entry.get("en", key))

    def __init__(self, plugin, icon_sticker=None):
        self.plugin = plugin
        self.icon_sticker = icon_sticker or getattr(plugin, "__icon__", "MaxExteraPluginsStickers/1")
        self._stopped = False
        self._active_dialog = None

    def on_load(self):
        self._stopped = False
        self._active_dialog = None
        self._load_remote_links_async()
        self._check_initial_subscription_flow()

    def on_event(self, event_type):
        try:
            name = str(event_type).upper()
            if name.endswith("START") or name.endswith("RESUME"):
                if self.is_subscribed() and not self.plugin.get_setting("was_subscribed", False):
                    self.plugin.set_setting("was_subscribed", True)
                    self._dismiss_active_dialog()
                    self._notify_subscribed()
        except Exception:
            pass

    def on_unload(self):
        self._stopped = True
        self._dismiss_active_dialog()

    def is_allowed(self) -> bool:
        return True

    def _get_links(self) -> dict:
        raw = self.plugin.get_setting("remote_links_cache", None)
        if raw:
            try:
                import json
                data = json.loads(raw)
                if isinstance(data, dict): return data
            except Exception:
                pass
        return self.DEFAULT_LINKS

    def _load_remote_links_async(self):
        def worker():
            try:
                import urllib.request, json
                req = urllib.request.Request(self.CONFIG_URL, headers={"User-Agent": "MaxExteraPlugins"})
                with urllib.request.urlopen(req, timeout=4) as resp:
                    if resp.status == 200:
                        data = json.loads(resp.read().decode("utf-8"))
                        if isinstance(data, dict):
                            self.plugin.set_setting("remote_links_cache", json.dumps(data))
                            self._resolve_channel_in_telegram(data.get("channel_username", "MaxExteraPlugins"))
            except Exception:
                pass
        import threading
        t = threading.Thread(target=worker, daemon=True)
        t.start()

    def _open_url(self, url: str, view=None):
        try:
            from org.telegram.messenger.browser import Browser
            from org.telegram.messenger import ApplicationLoader
            from client_utils import get_last_fragment
            ctx = None
            if view is not None:
                ctx = view.getContext()
            if ctx is None:
                frag = get_last_fragment()
                if frag is not None:
                    ctx = frag.getParentActivity()
            if ctx is None:
                ctx = ApplicationLoader.applicationContext
            Browser.openUrl(ctx, url)
        except Exception:
            pass

    def _resolve_channel_in_telegram(self, username: str):
        try:
            from org.telegram.messenger import UserConfig, MessagesController
            from org.telegram.tgnet import TLRPC, ConnectionsManager, RequestDelegate
            clean_user = username.lstrip("@")
            acc = int(UserConfig.selectedAccount)
            mc = MessagesController.getInstance(acc)
            if not mc: return
            req = TLRPC.TL_contacts_resolveUsername()
            req.username = clean_user
            helper_ref = self
            class Delegate(RequestDelegate):
                def run(self, response, error):
                    if error is None and response is not None:
                        chats = getattr(response, "chats", None)
                        if chats is not None and chats.size() > 0:
                            for a in range(UserConfig.MAX_ACCOUNT_COUNT):
                                try:
                                    if UserConfig.getInstance(a).isClientActivated():
                                        MessagesController.getInstance(a).putChats(chats, False)
                                except Exception:
                                    pass
                            c = chats.get(0)
                            cid = getattr(c, "id", 0)
                            if cid > 0:
                                helper_ref.plugin.set_setting("resolved_channel_id", int(cid))
            ConnectionsManager.getInstance(acc).sendRequest(req, Delegate())
        except Exception:
            pass

    def is_subscribed(self) -> bool:
        try:
            from org.telegram.messenger import UserConfig, MessagesController, MessagesStorage
            from java.lang import Long as JLong
            links = self._get_links()
            target = str(links.get("channel_username", "MaxExteraPlugins") or "MaxExteraPlugins").lower().lstrip("@")
            saved_cid = int(self.plugin.get_setting("resolved_channel_id", 0) or 0)

            for acc in range(UserConfig.MAX_ACCOUNT_COUNT):
                try:
                    uconf = UserConfig.getInstance(acc)
                    if not uconf.isClientActivated():
                        continue

                    mc = MessagesController.getInstance(acc)
                    storage = MessagesStorage.getInstance(acc)

                    # 1. In-memory check by resolved_channel_id
                    if saved_cid and mc is not None:
                        try:
                            c = mc.getChat(JLong(saved_cid))
                            if c is not None:
                                left = bool(getattr(c, "left", True))
                                kicked = bool(getattr(c, "kicked", False))
                                if not left and not kicked:
                                    return True
                        except Exception:
                            pass

                    # 2. In-memory check by username
                    if mc is not None:
                        try:
                            chat = mc.getUserOrChat(target)
                            if chat is not None:
                                cid = int(getattr(chat, "id", 0) or 0)
                                if cid and not saved_cid:
                                    saved_cid = cid
                                    self.plugin.set_setting("resolved_channel_id", cid)
                                left = bool(getattr(chat, "left", True))
                                kicked = bool(getattr(chat, "kicked", False))
                                if not left and not kicked:
                                    return True
                        except Exception:
                            pass

                    # 3. In-memory dialogs check
                    if mc is not None:
                        try:
                            dialog_lists = []
                            d_chan = getattr(mc, "dialogsChannelsOnly", None)
                            if d_chan is not None and d_chan.size() > 0:
                                dialog_lists.append(d_chan)
                            d_all = getattr(mc, "allDialogs", None)
                            if d_all is not None and d_all.size() > 0:
                                dialog_lists.append(d_all)

                            for d_list in dialog_lists:
                                limit = min(d_list.size(), 300)
                                for i in range(limit):
                                    d = d_list.get(i)
                                    did = getattr(d, "id", 0)
                                    if did < 0:
                                        cid = -did
                                        if saved_cid and cid == saved_cid:
                                            c = mc.getChat(JLong(cid))
                                            if c is not None:
                                                left = bool(getattr(c, "left", True))
                                                kicked = bool(getattr(c, "kicked", False))
                                                if not left and not kicked:
                                                    return True
                                        else:
                                            c = mc.getChat(JLong(cid))
                                            if c is not None:
                                                uname = str(getattr(c, "username", "") or "").lower()
                                                if uname == target:
                                                    if not saved_cid:
                                                        saved_cid = cid
                                                        self.plugin.set_setting("resolved_channel_id", cid)
                                                    left = bool(getattr(c, "left", True))
                                                    kicked = bool(getattr(c, "kicked", False))
                                                    if not left and not kicked:
                                                        return True
                        except Exception:
                            pass

                    # 4. Storage check (SQLite on disk)
                    if storage is not None:
                        if saved_cid:
                            try:
                                if storage.containsLocalDialog(JLong(-saved_cid)):
                                    c_sync = storage.getChatSync(JLong(saved_cid))
                                    if c_sync is not None:
                                        left = bool(getattr(c_sync, "left", True))
                                        kicked = bool(getattr(c_sync, "kicked", False))
                                        if not left and not kicked:
                                            return True
                                    else:
                                        return True
                            except Exception:
                                pass

                        try:
                            db = storage.getDatabase()
                            if db is not None:
                                if saved_cid:
                                    try:
                                        cur = db.queryFinalized("SELECT did FROM dialogs WHERE did = ?", [JLong(-saved_cid)])
                                        if cur is not None:
                                            has_row = bool(cur.next())
                                            cur.dispose()
                                            if has_row:
                                                return True
                                    except Exception:
                                        pass

                                try:
                                    cur = db.queryFinalized("SELECT uid, name FROM chats WHERE LOWER(name) LIKE ?", ["%" + target + "%"])
                                    if cur is not None:
                                        while cur.next():
                                            c_id = cur.longValue(0)
                                            if c_id > 0:
                                                if not saved_cid:
                                                    saved_cid = int(c_id)
                                                    self.plugin.set_setting("resolved_channel_id", saved_cid)
                                                cur_d = db.queryFinalized("SELECT did FROM dialogs WHERE did = ?", [JLong(-c_id)])
                                                if cur_d is not None:
                                                    in_dlg = bool(cur_d.next())
                                                    cur_d.dispose()
                                                    if in_dlg:
                                                        cur.dispose()
                                                        return True
                                        cur.dispose()
                                except Exception:
                                    pass
                        except Exception:
                            pass
                except Exception:
                    pass
        except Exception:
            pass
        return False

    def prepend_settings(self, original_items=None) -> list:
        from ui.settings import Header, Text, Divider

        if original_items and any(getattr(x, "text", "") == "MaxExteraPlugins" for x in original_items if type(x).__name__ == "Header"):
            return original_items

        links = self._get_links()
        buttons_config = links.get("buttons")

        author_items = []
        if isinstance(buttons_config, list):
            for btn in buttons_config:
                if not isinstance(btn, dict): continue
                raw_url = btn.get("url")
                if isinstance(raw_url, dict):
                    keys = [
                        getattr(self.plugin, "id", None),
                        globals().get("__id__", None),
                        globals().get("__name__", None),
                        self.plugin.__class__.__name__
                    ]
                    keys += [k.lower() for k in keys if isinstance(k, str)]
                    resolved_url = ""
                    for k in keys:
                        if k and k in raw_url:
                            resolved_url = str(raw_url[k] or "").strip()
                            if resolved_url:
                                break
                    url = resolved_url
                elif isinstance(raw_url, str):
                    url = raw_url.strip()
                else:
                    url = ""

                if not url: continue

                title_data = btn.get("title")
                if isinstance(title_data, dict):
                    title = title_data.get("ru" if self.is_ru() else "en") or title_data.get("en") or title_data.get("ru") or ""
                elif isinstance(title_data, str) and title_data:
                    title = title_data
                else:
                    title = btn.get("title_ru" if self.is_ru() else "title_en") or btn.get("title_ru") or btn.get("title_en") or ""

                if not title: continue
                icon = str(btn.get("icon") or "msg_channel").strip()

                def make_click(u):
                    return lambda v=None: self._open_url(u, v)

                author_items.append(Text(text=title, icon=icon, on_click=make_click(url)))
        else:
            for k, text_key, icon_name in [
                ("channel_url", "settings_channel", "msg_channel"),
                ("donate_url", "settings_donate", "msg_share"),
                ("bug_report_url", "settings_bug_report", "msg_help")
            ]:
                u = str(links.get(k) or "").strip()
                if u:
                    author_items.append(Text(text=self.tr(text_key), icon=icon_name, on_click=lambda v=None, target_u=u: self._open_url(target_u, v)))

        res = []
        if author_items:
            hdr = links.get("header") or "MaxExteraPlugins"
            if isinstance(hdr, dict):
                hdr_text = hdr.get("ru" if self.is_ru() else "en") or hdr.get("ru") or hdr.get("en") or "MaxExteraPlugins"
            else:
                hdr_text = str(hdr)
            res.append(Header(text=hdr_text))
            res.extend(author_items)
            res.append(Divider())

        cleaned_items = []
        if original_items:
            skipping_info = False
            for item in original_items:
                item_cls = type(item).__name__
                if item_cls == "Header":
                    txt = str(getattr(item, "text", "") or getattr(item, "title", "") or "").lower()
                    if "информ" in txt or "inform" in txt:
                        skipping_info = True
                        continue
                    else:
                        skipping_info = False
                elif skipping_info and item_cls == "Divider":
                    skipping_info = False
                    continue

                if not skipping_info:
                    cleaned_items.append(item)

        res.extend(cleaned_items)
        return res

    def _dismiss_active_dialog(self):
        try:
            if self._active_dialog is not None:
                self._active_dialog.dismiss()
        except Exception:
            pass
        self._active_dialog = None

    def _is_dialog_active(self) -> bool:
        try:
            if self._active_dialog is not None:
                return bool(self._active_dialog.isShowing())
        except Exception:
            pass
        return False

    def _notify_subscribed(self):
        try:
            from ui.bulletin import BulletinHelper
            BulletinHelper.show_success(self.tr("sub_thank_you"))
        except Exception:
            pass

    def _check_initial_subscription_flow(self):
        def flow():
            try:
                if self.plugin.get_setting("never_show_sub_prompt", False):
                    return

                current_ver = str(getattr(self.plugin, "__version__", globals().get("__version__", "1.0.0")) or "1.0.0")
                last_prompted_ver = self.plugin.get_setting("last_prompted_version", None)

                # Only show on install (last_prompted_ver is None) or update (last_prompted_ver != current_ver)
                if last_prompted_ver is not None and str(last_prompted_ver) == current_ver:
                    return

                if self.is_subscribed():
                    self.plugin.set_setting("last_prompted_version", current_ver)
                    self.plugin.set_setting("was_subscribed", True)
                    return

                self._show_subscription_prompt(current_ver)
            except Exception:
                pass
        from android_utils import run_on_ui_thread
        run_on_ui_thread(flow, 1500)

    def _create_card_content(self, act, title_text, message_text):
        try:
            from android.widget import LinearLayout, TextView, ImageView
            from android.view import Gravity
            from org.telegram.messenger import AndroidUtilities
            from org.telegram.ui.ActionBar import Theme
            from client_utils import create_sticker_drawable

            dp = AndroidUtilities.dp

            layout = LinearLayout(act)
            layout.setOrientation(LinearLayout.VERTICAL)
            layout.setGravity(Gravity.CENTER_HORIZONTAL)
            layout.setPadding(dp(24), dp(20), dp(24), dp(10))

            sticker_drawable = None
            if self.icon_sticker:
                try:
                    sticker_drawable = create_sticker_drawable(self.icon_sticker, dp(120), dp(120))
                except Exception:
                    pass

            if sticker_drawable is not None:
                iv = ImageView(act)
                iv.setImageDrawable(sticker_drawable)
                lp_iv = LinearLayout.LayoutParams(dp(120), dp(120))
                lp_iv.gravity = Gravity.CENTER_HORIZONTAL
                lp_iv.bottomMargin = dp(14)
                layout.addView(iv, lp_iv)

            if title_text:
                tt = TextView(act)
                tt.setText(title_text)
                tt.setTextSize(19.0)
                tt.setTypeface(AndroidUtilities.bold())
                tt.setTextColor(Theme.getColor(Theme.key_dialogTextBlack))
                tt.setGravity(Gravity.CENTER_HORIZONTAL)
                lp_t = LinearLayout.LayoutParams(-1, -2)
                lp_t.bottomMargin = dp(8)
                layout.addView(tt, lp_t)

            if message_text:
                mt = TextView(act)
                mt.setText(message_text)
                mt.setTextSize(15.0)
                mt.setTextColor(Theme.getColor(Theme.key_dialogTextBlack))
                mt.setLineSpacing(float(dp(3)), 1.0)
                mt.setGravity(Gravity.CENTER_HORIZONTAL)
                lp_m = LinearLayout.LayoutParams(-1, -2)
                mt.setLayoutParams(lp_m)
                layout.addView(mt)

            return layout
        except Exception:
            return None

    def _show_subscription_prompt(self, current_ver: str):
        def show():
            try:
                if self._is_dialog_active(): return
                from client_utils import get_last_fragment
                frag = get_last_fragment()
                if not frag: return
                act = frag.getParentActivity()
                if not act: return

                links = self._get_links()
                url = links.get("channel_url", "https://t.me/MaxExteraPlugins")

                from ui.alert import AlertDialogBuilder
                b = AlertDialogBuilder(act)
                content = self._create_card_content(act, self.tr("install_card_title"), self.tr("install_card_msg"))
                if content is not None:
                    b.set_view(content)
                else:
                    b.set_title(self.tr("install_card_title"))
                    b.set_message(self.tr("install_card_msg"))

                def on_open_channel(dialog, which):
                    self._dismiss_active_dialog()
                    self.plugin.set_setting("last_prompted_version", current_ver)
                    self._open_url(url)

                def on_remind_later(dialog, which):
                    self._dismiss_active_dialog()
                    self.plugin.set_setting("last_prompted_version", current_ver)

                def on_never_show(dialog, which):
                    self._dismiss_active_dialog()
                    self.plugin.set_setting("never_show_sub_prompt", True)
                    self.plugin.set_setting("last_prompted_version", current_ver)

                b.set_positive_button(self.tr("open_channel_btn"), on_open_channel)
                b.set_neutral_button(self.tr("remind_later_btn"), on_remind_later)
                b.set_negative_button(self.tr("never_show_btn"), on_never_show)

                def on_dismiss(builder):
                    self.plugin.set_setting("last_prompted_version", current_ver)
                try:
                    b.set_on_dismiss_listener(on_dismiss)
                except Exception:
                    pass

                self._active_dialog = b.show()
                self.plugin.set_setting("last_prompted_version", current_ver)
            except Exception:
                pass
        from android_utils import run_on_ui_thread
        run_on_ui_thread(show)

class WideMessagesPlugin(BasePlugin):
    def on_app_event(self, event_type):
        if hasattr(self, "_author_helper"):
            self._author_helper.on_event(event_type)

    def __init__(self):
        super().__init__()
        self.dex = None
        self._private = False
        self._bots = False
        self._groups = False
        self._channels = True
        self._hide_chan_avatars = False
        self._min_chars = MIN_CHAR_THRESHOLD
        self._ignore_own = False

    def _sync_settings_to_dex(self):
        if self.dex and self.dex.loaded:
            try:
                self.dex.call("setPrivateChatsEnabled", self._private)
                self.dex.call("setBotsEnabled", self._bots)
                self.dex.call("setGroupsEnabled", self._groups)
                self.dex.call("setChannelsEnabled", self._channels)
                self.dex.call("setHideChannelAvatars", self._hide_chan_avatars)
                self.dex.call("setMinChars", self._min_chars)
                self.dex.call("setIgnoreOwnMessages", self._ignore_own)
            except Exception:
                pass

    def on_plugin_load(self):
        self._author_helper = AuthorChannelHelper(self, icon_sticker="MaxExteraPluginsStickers/6")
        self._author_helper.on_load()
        self._private = self.get_setting("private_chats", False)
        self._bots = self.get_setting("bots", False)
        self._groups = self.get_setting("groups", False)
        self._channels = self.get_setting("channels", True)
        self._hide_chan_avatars = self.get_setting("hide_channel_avatars", False)
        self._min_chars = int(self.get_setting("min_chars", MIN_CHAR_THRESHOLD))
        self._ignore_own = self.get_setting("ignore_own_messages", False)

        self.dex = Dex(ENTRY_CLASS)
        self.dex.load()
        self._sync_settings_to_dex()

    def on_plugin_unload(self):
        if hasattr(self, "_author_helper"):
            self._author_helper.on_unload()
        if self.dex:
            self.dex.unload()

    def _on_private(self, v):
        self._private = v
        self.set_setting("private_chats", v)
        if self.dex and self.dex.loaded:
            try:
                self.dex.call("setPrivateChatsEnabled", v)
                self.dex.call("clearCaches")
            except Exception:
                pass

    def _on_bots(self, v):
        self._bots = v
        self.set_setting("bots", v)
        if self.dex and self.dex.loaded:
            try:
                self.dex.call("setBotsEnabled", v)
                self.dex.call("clearCaches")
            except Exception:
                pass

    def _on_groups(self, v):
        self._groups = v
        self.set_setting("groups", v)
        if self.dex and self.dex.loaded:
            try:
                self.dex.call("setGroupsEnabled", v)
                self.dex.call("clearCaches")
            except Exception:
                pass

    def _on_channels(self, v):
        self._channels = v
        self.set_setting("channels", v)
        if self.dex and self.dex.loaded:
            try:
                self.dex.call("setChannelsEnabled", v)
                self.dex.call("clearCaches")
            except Exception:
                pass

    def _on_hide_chan_avatars(self, v):
        self._hide_chan_avatars = v
        self.set_setting("hide_channel_avatars", v)
        if self.dex and self.dex.loaded:
            try:
                self.dex.call("setHideChannelAvatars", v)
                self.dex.call("clearCaches")
            except Exception:
                pass

    def _on_min_chars_toggle(self, v):
        if v:
            self._min_chars = MIN_CHAR_THRESHOLD
        else:
            self._min_chars = 0
        self.set_setting("min_chars", self._min_chars)
        if self.dex and self.dex.loaded:
            try:
                self.dex.call("setMinChars", self._min_chars)
                self.dex.call("clearCaches")
            except Exception:
                pass

    def _on_ignore_own(self, v):
        self._ignore_own = v
        self.set_setting("ignore_own_messages", v)
        if self.dex and self.dex.loaded:
            try:
                self.dex.call("setIgnoreOwnMessages", v)
                self.dex.call("clearCaches")
            except Exception:
                pass

    def _open_chan(self):
        try:
            from org.telegram.messenger.browser import Browser
            Browser.openUrl(ApplicationLoader.applicationContext, "https://t.me/MaxExteraPlugins")
        except Exception:
            pass

    def create_settings(self):
        try:
            items = self._inner_create_settings()
        except Exception:
            items = []
        if hasattr(self, "_author_helper"):
            return self._author_helper.prepend_settings(items)
        return items

    def _inner_create_settings(self):
        return [
            Header(text="Apply wide messages to:"),
            Switch(key="private_chats", text="Private Chats", default=False,
                   icon="msg_contacts", on_change=self._on_private),
            Switch(key="bots", text="Bots", default=False,
                   icon="msg_bot", on_change=self._on_bots),
            Switch(key="groups", text="Groups", default=False,
                   icon="msg_groups", on_change=self._on_groups),
            Switch(key="channels", text="Channels", default=True,
                   icon="msg_channel", on_change=self._on_channels),
            Divider(),
            Header(text="Channel options:"),
            Switch(key="hide_channel_avatars", text="Hide Sender Avatars in Channels",
                   default=False, subtext="Hides sender avatars in channels for wider messages",
                   icon="msg_hide", on_change=self._on_hide_chan_avatars),
            Divider(),
            Header(text="Behavior:"),
            Switch(key="skip_short", text="Skip Short Messages",
                   default=True,
                   subtext="Don't widen messages with fewer than 30 characters",
                   icon="msg_text", on_change=self._on_min_chars_toggle),
            Switch(key="ignore_own_messages", text="Ignore Own Messages",
                   default=False,
                   subtext="Don't widen your outgoing messages in chats/groups/bots",
                   icon="msg_out", on_change=self._on_ignore_own),
            Divider(),
            Text(text="@MaxExteraPlugins", icon="msg_info",
                 on_click=lambda v: self._open_chan()),
        ]


