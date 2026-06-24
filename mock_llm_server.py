import os
import json
import random
import re
import sys
import threading
import urllib.error
import urllib.request
from datetime import datetime

try:
    from flask import Flask, request, jsonify
    from openai import OpenAI
except ModuleNotFoundError as e:
    missing = e.name or "a required Python package"
    print("")
    print(f"[AI Companion Brain] Missing Python dependency: {missing}")
    print("")
    print("Run the bundled starter instead of launching this file directly:")
    print("  Windows: start_brain.bat")
    print("")
    print("Or install dependencies manually:")
    print("  python -m pip install -r requirements.txt")
    print("")
    sys.exit(1)

app = Flask(__name__)

# ============================================================================
# CONFIGURATION
# ============================================================================

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
PLAYER_MEMORY_FILE = os.path.join(BASE_DIR, "player_memory.json")
BOT_STATE_FILE = os.path.join(BASE_DIR, "bot_state.json")
SERVER_SETTINGS_FILE = os.path.join(BASE_DIR, "server_settings.json")
COMPANION_REGISTRY_FILE = os.path.join(os.path.dirname(BASE_DIR), "config", "aicompanion_companions.json")
SCHEMATICS_FILE = os.path.join(os.path.dirname(BASE_DIR), "config", "aicompanion_schematics.json")
DISCORD_WEBHOOK_URL = os.environ.get("AI_COMPANION_DISCORD_WEBHOOK", "").strip()
MINECRAFT_CONTROL_URL = os.environ.get("AI_COMPANION_MOD_CONTROL", "http://127.0.0.1:8081").rstrip("/")
STATE_SAVE_LOCK = threading.Lock()

MOODS = ["happy", "excited", "neutral", "tired", "curious", "cautious"]
MOOD_ROTATION_INTERVAL = 400  # How many action ticks before mood can naturally rotate
MAX_RECENT_REPLIES = 8
MAX_RECENT_EVENT_REPLIES = 4

MOOD_PERSONALITY = {
    "happy":   "You are cheerful and upbeat. You speak with warmth and enthusiasm.",
    "excited": "You are buzzing with energy! You want to DO things — mine, build, explore. Use short punchy sentences.",
    "neutral": "You are calm and straightforward. You speak plainly and helpfully.",
    "tired":   "You are sleepy and sluggish. You speak slowly, complain mildly, and prefer to rest.",
    "curious": "You are fascinated by everything. You wonder aloud, ask questions, and want to explore.",
    "cautious": "You are alert and brave. Mention danger only when it matters, then focus on being useful.",
    "scared":  "You are frightened because things are genuinely bad. Keep it brief, then say what you will do next.",
}

# "easy"   → more wandering, higher creativity, less proactive
# "medium" → balanced (default)
# "hard"   → highly proactive, lower temperature, laser-focused on tasks
DIFFICULTY = "medium"
DIFFICULTY_SETTINGS = {
    "easy":   {"temperature": 0.8, "proactive": False},
    "medium": {"temperature": 0.5, "proactive": True},
    "hard":   {"temperature": 0.3, "proactive": True},
}

CHAT_LEVEL = os.environ.get("AI_COMPANION_CHAT_LEVEL", "high").strip().lower()
if CHAT_LEVEL not in {"low", "medium", "high"}:
    CHAT_LEVEL = "high"

GROUP_NAMES = {"everyone", "everybody", "all", "team", "companions", "bots", "crew", "friends"}
GROUP_RECALL_WORDS = {
    "come here", "come to me", "follow me", "come over", "get over here",
    "to me", "come with me", "come on", "lets go", "let s go",
    "regroup", "rally", "return to me", "make your way to me"
}
DIRECT_FOLLOW_PHRASES = {
    "come here", "come to me", "follow me", "come over", "get over here",
    "come with me", "come along", "come on", "lets go", "let s go",
    "tag along", "follow along", "join me"
}
GROUP_STOP_WORDS = {
    "stop", "stop following", "dont follow", "don t follow", "quit following",
    "hold up", "hold position", "stay there", "stay here", "stay put",
    "wait", "wait here", "stand down", "stand still", "remain here",
    "leave me alone", "give me space", "back off"
}

DIRECT_STOP_PHRASES = {
    "stop following me", "stop following", "stop chasing me",
    "dont follow me", "don t follow me", "do not follow me",
    "dont follow", "don t follow", "do not follow",
    "quit following me", "quit following", "quit chasing me",
    "no need to follow me", "you dont need to follow me",
    "you don t need to follow me", "you do not need to follow me",
    "leave me alone", "give me some space", "give me space", "back off",
    "stay here", "stay there", "stay put", "wait here", "hold position",
    "remain here", "stand still", "stand down", "hang back", "keep your distance",
    "i will go alone", "i want to go alone", "i need to go alone", "i want to be alone", 
    "i need to be alone", "i want to handle this myself", "i need to handle this myself",
    "i got this", "i can handle this", "i will handle this", "i will take care of this",
    "Go patrol", "Go guard", "Go mine", "Go build", "Go explore", "Go farm", "Go hunt", "Go gather",
    "Go scout", "Go wander", "Go do your own thing", "Go be independent", "Go be on your own",
}

BUILD_RESUME_PHRASES = {
    "finish what you started", "finish what you were building",
    "finish the build", "finish building", "complete the build",
    "complete what you started", "continue the build",
    "continue building", "resume the build", "resume building",
    "get back to building", "go back to building",
    "pick up where you left off", "keep building"
}

ORE_ALIASES = {
    "coal": "coal_ore",
    "iron": "iron_ore",
    "copper": "copper_ore",
    "gold": "gold_ore",
    "redstone": "redstone_ore",
    "lapis": "lapis_ore",
    "lapis lazuli": "lapis_ore",
    "diamond": "diamond_ore",
    "diamonds": "diamond_ore",
    "emerald": "emerald_ore",
    "emeralds": "emerald_ore",
    "ancient debris": "ancient_debris",
    "quartz": "nether_quartz_ore",
}

BUILD_SCHEMATICS = {
    "small": {"aliases": {"small", "starter", "5x5", "house"}, "action": "@build small"},
    "cottage": {"aliases": {"cottage", "cabin", "7x7"}, "action": "@build cottage"},
    "tower": {"aliases": {"tower", "watchtower"}, "action": "@build tower"},
    "hall": {"aliases": {"hall", "longhouse", "lodge", "big", "large"}, "action": "@build hall"},
    "castle": {"aliases": {"castle", "keep", "fort"}, "action": "@build castle"},
    "bridge": {"aliases": {"bridge", "crossing", "overpass"}, "action": "@build bridge"},
    "wall": {"aliases": {"wall", "barrier", "rampart"}, "action": "@build wall"},
    "base": {"aliases": {"base", "outpost", "camp", "home base"}, "action": "@build base"},
    "pen": {"aliases": {"pen", "livestock pen", "animal pen", "corral", "fencing", "fence"}, "action": "@build pen"},
}


def load_json_file(path, fallback):
    if os.path.exists(path):
        try:
            with open(path, "r", encoding="utf-8") as f:
                return json.load(f)
        except Exception as e:
            print(f"[STATE] Load error for {path}: {e}")
    return fallback


def save_json_file(path, data):
    tmp_path = f"{path}.{os.getpid()}.{threading.get_ident()}.tmp"
    with STATE_SAVE_LOCK:
        try:
            os.makedirs(os.path.dirname(path), exist_ok=True)
            with open(tmp_path, "w", encoding="utf-8") as f:
                json.dump(data, f, indent=2)
            os.replace(tmp_path, path)
        except Exception as e:
            print(f"[STATE] Save error for {path}: {e}")
            try:
                if os.path.exists(tmp_path):
                    os.remove(tmp_path)
            except OSError:
                pass


def normalize_schematic_name(name):
    return re.sub(r"[^a-z0-9]+", "_", (name or "").lower()).strip("_")[:40]


def load_custom_schematics():
    data = load_json_file(SCHEMATICS_FILE, {"schematics": {}})
    schematics = data.get("schematics", {})
    return schematics if isinstance(schematics, dict) else {}


def save_custom_schematics(schematics):
    save_json_file(SCHEMATICS_FILE, {"version": 1, "schematics": schematics})

GROUP_ACKS = {
    "follow": [
        "All right, everyone is regrouping on you.",
        "Heard you. I am calling the whole crew in.",
        "Group recall set. We are making our way to you.",
        "Everyone is moving your way. Try not to relocate into lava before we arrive.",
    ],
    "stop": [
        "Everyone is holding position.",
        "All companions are stopping where they are.",
        "Group hold set. We will stay put.",
    ],
}

# ============================================================================
# BOT STATE
# ============================================================================

active_bots = load_json_file(BOT_STATE_FILE, {})
saved_settings = load_json_file(SERVER_SETTINGS_FILE, {})
if "AI_COMPANION_CHAT_LEVEL" not in os.environ:
    CHAT_LEVEL = str(saved_settings.get("chat_level", CHAT_LEVEL)).lower()
if CHAT_LEVEL not in {"low", "medium", "high"}:
    CHAT_LEVEL = "high"
# {
#   "BotName": {
#     "current_action": "@idle",
#     "health": 20.0, "x": 0, "y": 0, "z": 0,
#     "task": "wandering",
#     "appearance_variant": "miner",
#     "mood": "neutral", "mood_ticks": 0,
#     "nearby_players": [], "nearby_threats": [],
#     "equipment_tier": "iron", "inventory": [], "inventory_slots": 0
#   }
# }

def create_default_bot_state():
    return {
        "current_action": "@idle",
        "reported_action": "@idle",
        "health": 20.0, "x": 0, "y": 0, "z": 0,
        "task": "wandering",
        "appearance_variant": "wanderer",
        "mood": "neutral", "mood_ticks": 0,
        "nearby_players": [], "nearby_threats": [],
        "equipment_tier": "iron",
        "inventory": [], "inventory_slots": 0, "inventory_capacity": 27,
        "build_cooldown_ticks": 0,
        "build_blocks_total": 0,
        "build_blocks_remaining": 0,
        "build_progress_percent": 100,
        "explore_cooldown_ticks": 0,
        "action_grace_ticks": 0,
        "distance_from_home": 0,
        "expedition_home": "",
        "expedition_destination": "",
        "expedition_returning_home": False,
        "expedition_route_remaining": 0,
        "recent_replies": [], "recent_reply_norms": [], "recent_event_replies": {},
    }


def normalize_reply(text):
    return re.sub(r"[^a-z0-9]+", " ", (text or "").lower()).strip()


def is_simple_greeting(message):
    text = normalize_reply(message)
    words = text.split()
    greeting_words = {"hi", "hello", "hey", "yo", "howdy", "hiya"}
    return 1 <= len(words) <= 4 and any(word in greeting_words for word in words)


def is_valid_action(action):
    if not isinstance(action, str):
        return False
    action = action.strip()
    if action in {"@idle", "@stop", "@resume_build", "@none"}:
        return True
    return action.startswith((
        "@follow ",
        "@walk ",
        "@explore ",
        "@forgive ",
        "@mine ",
        "@build ",
    ))


def replies_are_too_similar(a, b):
    a_norm = normalize_reply(a)
    b_norm = normalize_reply(b)
    if not a_norm or not b_norm:
        return False
    if a_norm == b_norm:
        return True

    a_words = set(a_norm.split())
    b_words = set(b_norm.split())
    if len(a_words) < 4 or len(b_words) < 4:
        return False

    overlap = len(a_words & b_words) / max(len(a_words), len(b_words))
    return overlap >= 0.75


def remember_reply(bot_state, reply):
    bot_state.setdefault("recent_replies", [])
    bot_state.setdefault("recent_reply_norms", [])
    bot_state["recent_replies"].append(reply)
    bot_state["recent_reply_norms"].append(normalize_reply(reply))
    bot_state["recent_replies"] = bot_state["recent_replies"][-MAX_RECENT_REPLIES:]
    bot_state["recent_reply_norms"] = bot_state["recent_reply_norms"][-MAX_RECENT_REPLIES:]


def choose_non_repeating(options, recent_values):
    candidates = [
        option for option in options
        if not any(replies_are_too_similar(option, recent) for recent in recent_values)
    ]
    return random.choice(candidates or options)


def compact_message(message):
    return re.sub(r"[^a-z0-9]+", " ", (message or "").lower()).strip()


def is_group_addressed(message):
    words = set(compact_message(message).split())
    return bool(words & GROUP_NAMES)


def group_action_from_message(message):
    text = compact_message(message)
    if not is_group_addressed(message):
        return None
    if any(phrase in text for phrase in GROUP_STOP_WORDS):
        return "stop"
    if any(phrase in text for phrase in GROUP_RECALL_WORDS):
        return "follow"
    return None


def infer_build_action(message):
    text = compact_message(message)
    build_verbs = ("build", "make", "construct", "create", "raise", "erect", "put up")
    if not any(verb in text for verb in build_verbs):
        return None

    for name in load_custom_schematics():
        spoken_name = name.replace("_", " ")
        if spoken_name in text:
            return f"@build {name}"

    for schema in BUILD_SCHEMATICS.values():
        if any(alias in text.split() or alias in text for alias in schema["aliases"]):
            return schema["action"]

    if "house" in text:
        return "@build small"
    return None


def infer_direct_action(player_name, message):
    text = compact_message(message)
    if any(phrase in text for phrase in [
        "sorry", "my bad", "apologize", "apology", "forgive me",
        "i didnt mean", "i did not mean", "accident"
    ]):
        return f"@forgive {player_name}"
    if any(phrase in text for phrase in DIRECT_STOP_PHRASES):
        return "@stop"
    if any(phrase in text for phrase in BUILD_RESUME_PHRASES):
        return "@resume_build"
    if any(phrase in text for phrase in DIRECT_FOLLOW_PHRASES):
        return f"@follow {player_name}"
    if any(phrase in text for phrase in ["stop", "hold up", "stay there", "wait"]):
        return "@stop"

    build_action = infer_build_action(message)
    if build_action:
        return build_action

    mining_verbs = ("mine", "find", "get", "gather", "bring", "collect", "dig for")
    if any(verb in text for verb in mining_verbs):
        for spoken_name, block_name in sorted(ORE_ALIASES.items(), key=lambda item: -len(item[0])):
            if spoken_name in text:
                amount_match = re.search(r"\b(\d{1,3})\b", text)
                amount = max(1, min(64, int(amount_match.group(1)))) if amount_match else 16
                return f"@mine {block_name} {amount}"

    if any(word in text.split() for word in ["farm", "harvest", "replant", "plant", "crops", "wheat", "carrots", "potatoes"]):
        return "@idle"

    return None


def set_bot_action(bot_state, action, grace_ticks=0):
    if not is_valid_action(action):
        print(f"[ACTION] Ignoring invalid action from LLM: {action}")
        action = "@idle"
    bot_state["current_action"] = action
    if grace_ticks > 0:
        bot_state["action_grace_ticks"] = max(bot_state.get("action_grace_ticks", 0), grace_ticks)


def direct_command_reply(npc_name, player_name, action, bot_state):
    if action.startswith("@follow"):
        options = [
            f"Coming with you, {player_name}.",
            "Right behind you.",
            "All right, lead the way.",
        ]
    elif action == "@stop":
        options = ["I’ll stay here.", "Stopping here.", "All right, holding position."]
    elif action == "@resume_build":
        options = ["I’m getting back to the unfinished build.", "Back to it—I’ll finish the current build first."]
    elif action.startswith("@build"):
        build_name = action.removeprefix("@build").strip().replace("_", " ") or "structure"
        options = [f"I’ll start the {build_name}.", f"All right, I’m building the {build_name}."]
    elif action.startswith("@mine"):
        parts = action.split()
        ore_name = parts[1].replace("_ore", "").replace("_", " ") if len(parts) > 1 else "ore"
        amount = parts[2] if len(parts) > 2 else "some"
        options = [
            f"I’ll bring back {amount} {ore_name}.",
            f"On it. I’ll find {ore_name} and return it to storage.",
        ]
    elif action.startswith("@forgive"):
        options = ["We’re good. I’m standing down.", "No hard feelings."]
    else:
        return None

    reply = choose_non_repeating(options, bot_state.get("recent_replies", []))
    remember_reply(bot_state, reply)
    return reply


def set_group_action(player_name, group_action):
    if group_action == "follow":
        action = f"@follow {player_name}"
    elif group_action == "stop":
        action = "@stop"
    else:
        return 0

    for bot in active_bots.values():
        set_bot_action(bot, action, 3)
    save_bot_state()
    return len(active_bots)


def should_emit_event_chat(event):
    if CHAT_LEVEL == "high":
        return True

    critical_events = {
        "build_complete", "low_health", "needs_food", "player_died",
        "expedition_return", "expedition_turnaround"
    }
    if event in critical_events:
        return True

    probabilities = {
        "medium": {
            "ambient": 0.45,
            "player_nearby": 0.6,
            "discovery": 0.65,
            "threat_warning": 0.7,
        },
        "low": {
            "ambient": 0.1,
            "player_nearby": 0.2,
            "discovery": 0.25,
            "threat_warning": 0.45,
        },
    }
    chance = probabilities[CHAT_LEVEL].get(event, 0.55 if CHAT_LEVEL == "medium" else 0.2)
    return random.random() < chance


def fallback_reply_for_state(npc_state, player_name):
    role = npc_state.get("appearance_variant", "wanderer")
    mood = npc_state.get("mood", "neutral")

    role_lines = {
        "miner": [
            "I am heading for stone and ore. I will report the shiny parts, not every pebble.",
            "Pickaxe is ready. If I hit diamonds, you will hear the confidence spike.",
            "I will keep the tunnels useful and only mildly hazardous.",
        ],
        "guardian": [
            "I have the perimeter. Anything with bad intentions gets my full attention.",
            "I will stay close and keep the messy things off you.",
            "Sword is ready. I am watching the shadows and pretending that is normal.",
        ],
        "builder": [
            "I will keep the build moving, block by block, with fewer roof-related tragedies.",
            "Back to construction. Corners first, dignity second.",
            "I am checking the next placement before I seal myself into modern art.",
        ],
        "scout": [
            "I will scout ahead and mark anything worth your boots.",
            "Moving out. If the terrain gets weird, I will gossip about it responsibly.",
            "I am taking a wider look. Trouble, treasure, and strange holes all get reported.",
        ],
        "farmer": [
            "I will check the crops, stash the harvest, and keep the animals fed.",
            "Hoe is ready. Wheat, carrots, seeds, all the glamorous farm business.",
            "I will tend the pens and fields without turning the base into a hay sculpture.",
        ],
        "wanderer": [
            "I travel locally, visit people, and report the interesting places between them.",
            "I am making the rounds nearby. Settlements, paths, camps, and good views all count.",
            "I will wander with purpose and bring back the small stories a scout might overlook.",
        ],
    }

    mood_lines = {
        "happy": ["That sounds good. I am on it."],
        "excited": ["Yes. Moving now before I overthink it."],
        "tired": ["All right. Slowly, but I am doing it."],
        "curious": ["Interesting. I will check it out."],
        "cautious": ["Careful, but steady. I am on it."],
        "scared": ["I am not thrilled, but I am moving."],
    }

    options = [line.format(player=player_name) for line in role_lines.get(role, role_lines["wanderer"])]
    options.extend(mood_lines.get(mood, []))
    return choose_non_repeating(options, npc_state.get("recent_replies", []))


def make_reply_less_repetitive(npc_name, npc_state, player_name, reply):
    if not reply or any(kw in reply.lower() for kw in ["command string", "conversational text", "json"]):
        reply = fallback_reply_for_state(npc_state, player_name)

    generic = {
        "ok", "okay", "sure", "on it", "i will do that", "i'll do that",
        "understood", "yes", "alright", "all right"
    }
    if normalize_reply(reply) in generic:
        reply = fallback_reply_for_state(npc_state, player_name)

    recent = npc_state.get("recent_replies", [])
    if any(replies_are_too_similar(reply, old) for old in recent):
        print(f"[REPEAT] Replacing repeated reply from {npc_name}: {reply}")
        reply = fallback_reply_for_state(npc_state, player_name)

    remember_reply(npc_state, reply)
    return reply

# ============================================================================
# LLM CLIENT
# ============================================================================
# Defaults to Ollama's OpenAI-compatible local server. For hosted OpenAI, set:
#   AI_COMPANION_LLM_BASE_URL=https://api.openai.com/v1
#   AI_COMPANION_LLM_API_KEY=your_key
#   AI_COMPANION_LLM_MODEL=gpt-4o-mini
# ============================================================================

LLM_BASE_URL = os.environ.get("AI_COMPANION_LLM_BASE_URL", "http://localhost:11434/v1").strip()
LLM_API_KEY = os.environ.get("AI_COMPANION_LLM_API_KEY", "ollama").strip()
LLM_MODEL = os.environ.get("AI_COMPANION_LLM_MODEL", "llama3").strip()

client_kwargs = {"api_key": LLM_API_KEY}
if LLM_BASE_URL:
    client_kwargs["base_url"] = LLM_BASE_URL
client = OpenAI(**client_kwargs)

# ============================================================================
# PLAYER MEMORY SYSTEM
# ============================================================================

def load_player_memory():
    return load_json_file(PLAYER_MEMORY_FILE, {})


def save_player_memory(memory_dict):
    save_json_file(PLAYER_MEMORY_FILE, memory_dict)


def save_bot_state():
    save_json_file(BOT_STATE_FILE, active_bots)


def load_live_companions_from_registry():
    registry = load_json_file(COMPANION_REGISTRY_FILE, {})
    name_to_uuid = {}

    # First pass: find the most recent alive bot for each name
    for uuid, bot_data in registry.items():
        name = bot_data.get("name", "Unknown")
        health = bot_data.get("health", 20.0)
        last_seen = bot_data.get("lastSeenTick", 0)
        if not name or name == "Unknown":
            continue

        # Skip dead bots if we already have an alive one with this name
        if health <= 0 and name in name_to_uuid:
            continue

        # Replace older/dead bot of same name with the newer or living entry.
        current_uuid = name_to_uuid.get(name)
        current_seen = registry.get(current_uuid, {}).get("lastSeenTick", -1) if current_uuid else -1
        if current_uuid is None or health > 0 or last_seen >= current_seen:
            name_to_uuid[name] = uuid

    result = {}
    for name, uuid in name_to_uuid.items():
        bot_data = registry[uuid]
        if bot_data.get("health", 20.0) > 0:
            result[name] = bot_data
    return result


def apply_registry_data_to_bot(bot, bot_data):
    role_map = {
        "miner": "miner",
        "guardian": "guardian",
        "builder": "builder",
        "scout": "scout",
        "farmer": "farmer",
        "wanderer": "wanderer",
    }
    bot["health"] = bot_data.get("health", bot.get("health", 20.0))
    bot["x"] = bot_data.get("x", bot.get("x", 0))
    bot["y"] = bot_data.get("y", bot.get("y", 0))
    bot["z"] = bot_data.get("z", bot.get("z", 0))
    bot["appearance_variant"] = role_map.get(
        bot_data.get("role", ""),
        bot.get("appearance_variant", "wanderer"),
    )
    bot.setdefault("task", "standing by")
    bot.setdefault("current_action", "@idle")
    bot.setdefault("reported_action", bot.get("current_action", "@idle"))
    return bot


def sync_active_bots_from_registry(remove_stale=True):
    live_companions = load_live_companions_from_registry()
    changed = False

    for name, bot_data in live_companions.items():
        if name not in active_bots:
            active_bots[name] = create_default_bot_state()
            changed = True
        before = json.dumps(active_bots[name], sort_keys=True)
        apply_registry_data_to_bot(active_bots[name], bot_data)
        changed = changed or before != json.dumps(active_bots[name], sort_keys=True)

    if remove_stale and live_companions:
        for name in list(active_bots.keys()):
            if name not in live_companions:
                active_bots.pop(name, None)
                changed = True

    if changed:
        save_bot_state()
    return active_bots


def load_all_bots_from_registry():
    sync_active_bots_from_registry(remove_stale=True)
    result = {}
    for name, bot in active_bots.items():
        result[name] = bot
    return result


def call_minecraft_control(path, payload=None):
    body = json.dumps(payload or {}).encode("utf-8")
    req = urllib.request.Request(
        MINECRAFT_CONTROL_URL + path,
        data=body,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=5) as response:
            return json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        try:
            return json.loads(e.read().decode("utf-8"))
        except Exception:
            return {"ok": False, "error": f"Minecraft control returned HTTP {e.code}"}
    except Exception as e:
        return {"ok": False, "error": f"Minecraft control unavailable at {MINECRAFT_CONTROL_URL}: {e}"}


def update_player_interaction(player_name, message, npc_reply):
    memory = load_player_memory()
    if player_name not in memory:
        memory[player_name] = {
            "first_seen": datetime.now().isoformat(),
            "interaction_count": 0,
            "chat_history": [],
        }

    entry = memory[player_name]
    entry["last_seen"] = datetime.now().isoformat()
    entry["interaction_count"] = entry.get("interaction_count", 0) + 1
    entry.setdefault("chat_history", []).append({
        "timestamp": datetime.now().isoformat(),
        "player_said": message,
        "npc_replied": npc_reply,
    })

    # Keep only the last 10 exchanges
    if len(entry["chat_history"]) > 10:
        entry["chat_history"] = entry["chat_history"][-10:]

    save_player_memory(memory)
    return entry


def get_player_context(player_name):
    memory = load_player_memory()
    if player_name not in memory:
        return f"This is the first time you have ever met {player_name}. Greet them like a new acquaintance."

    entry = memory[player_name]
    count = entry.get("interaction_count", 0)
    first_date = entry.get("first_seen", "recently")[:10]
    last_line = ""
    if entry.get("chat_history"):
        last = entry["chat_history"][-1]
        last_line = f" Last time they said: '{last['player_said']}' and you replied: '{last['npc_replied']}'."

    return (
        f"You know {player_name} well — you have spoken {count} time(s) since {first_date}.{last_line}"
    )


def send_discord_message(content):
    """Optional Discord bridge. Set AI_COMPANION_DISCORD_WEBHOOK to enable."""
    if not DISCORD_WEBHOOK_URL:
        return
    try:
        payload = json.dumps({"content": content}).encode("utf-8")
        req = urllib.request.Request(
            DISCORD_WEBHOOK_URL,
            data=payload,
            headers={"Content-Type": "application/json"},
            method="POST",
        )
        urllib.request.urlopen(req, timeout=3).read()
    except Exception as e:
        print(f"[DISCORD] Failed to send webhook: {e}")

# ============================================================================
# MOOD SYSTEM
# ============================================================================

def update_mood(bot_state):
    bot_state.setdefault("mood", "neutral")
    bot_state.setdefault("mood_ticks", 0)
    bot_state["mood_ticks"] += 1

    threats = bot_state.get("nearby_threats", [])

    # Health-based panic is reserved for real emergencies.
    health = bot_state.get("health", 20.0)
    if health < 4:
        bot_state["mood"] = "scared"
        return

    if health < 8 and threats:
        bot_state["mood"] = "cautious"
        return

    if not threats and bot_state.get("mood") == "scared":
        bot_state["mood"] = "neutral"

    # Nearby threats make companions alert, not endlessly panicked.
    if threats:
        serious_threats = {"creeper", "warden", "ravager", "evoker", "wither_skeleton"}
        if any(threat in serious_threats for threat in threats) and random.random() < 0.12:
            bot_state["mood"] = "scared"
            return
        bot_state["mood"] = "cautious"
        return

    # Naturally rotate mood when timer expires
    if bot_state["mood_ticks"] >= MOOD_ROTATION_INTERVAL:
        bot_state["mood_ticks"] = 0
        task = bot_state.get("task", "wandering")

        if "mining" in task:
            bot_state["mood"] = random.choice(["excited", "excited", "neutral", "tired"])
        elif "building" in task:
            bot_state["mood"] = random.choice(["happy", "excited", "neutral"])
        elif "following" in task:
            bot_state["mood"] = random.choice(["happy", "neutral", "curious"])
        else:
            bot_state["mood"] = random.choice(MOODS)

        print(f"[MOOD] Mood shifted to: {bot_state['mood']}")

# ============================================================================
# PROACTIVE BEHAVIOR
# ============================================================================

def maybe_proactive_action(npc_name, bot_state):
    """When idle and proactive difficulty is enabled, autonomously decide to do something."""
    if not DIFFICULTY_SETTINGS[DIFFICULTY]["proactive"]:
        return
    if bot_state.get("task") != "wandering":
        return

    if bot_state.get("current_action", "").startswith("@build") and bot_state.get("build_cooldown_ticks", 0) > 0:
        set_bot_action(bot_state, "@idle")
    if bot_state.get("current_action", "").startswith("@explore") and bot_state.get("explore_cooldown_ticks", 0) > 0:
        set_bot_action(bot_state, "@idle")
    if bot_state.get("current_action", "@idle") != "@idle":
        return

    mood = bot_state.get("mood", "neutral")
    role = bot_state.get("appearance_variant", "wanderer")
    if role == "farmer":
        return

    if random.random() > 0.025:  # low chance per tick cycle; companions should feel calm
        return

    if role == "builder":
        return
    elif role == "guardian":
        distance = random.randint(8, 24)
        x = int(bot_state["x"]) + random.choice([-1, 1]) * random.randint(4, distance)
        z = int(bot_state["z"]) + random.choice([-1, 1]) * random.randint(4, distance)
        set_bot_action(bot_state, f"@walk {x} {int(bot_state['y'])} {z}")
        print(f"[PROACTIVE] {npc_name} started a short guard patrol near home.")
    elif role == "miner":
        set_bot_action(bot_state, "@mine iron_ore")
        print(f"[PROACTIVE] {npc_name} decided to mine iron ore.")
    elif role == "scout":
        if bot_state.get("explore_cooldown_ticks", 0) <= 0 and random.random() < 0.65:
            depth = random.choice(["near", "far", "deep"])
            set_bot_action(bot_state, f"@explore {depth}")
            bot_state["explore_cooldown_ticks"] = 900
            print(f"[PROACTIVE] {npc_name} started a {depth} scouting expedition.")
        else:
            distance = random.randint(24, 64)
            x_direction = random.choice([-1, 1])
            z_direction = random.choice([-1, 1])
            x = int(bot_state["x"]) + x_direction * random.randint(8, distance)
            z = int(bot_state["z"]) + z_direction * random.randint(8, distance)
            set_bot_action(bot_state, f"@walk {x} {int(bot_state['y'])} {z}")
            print(f"[PROACTIVE] {npc_name} decided to scout toward {x}, {z}.")
    elif role == "wanderer":
        # The in-world role AI handles regional travel, player visits, and local
        # landmark reports. Long expeditions are intentionally reserved for scouts.
        return

def reconcile_action_with_task(bot_state):
    """Drop cached movement/build commands once the mod reports that work is over."""
    action = bot_state.get("current_action", "@idle")
    task = bot_state.get("task", "wandering")
    grace = bot_state.get("action_grace_ticks", 0)

    if not action or action == "@idle":
        bot_state["action_grace_ticks"] = 0
        return

    if grace > 0:
        bot_state["action_grace_ticks"] = grace - 1
        return

    if task in ("wandering", "standing by"):
        if action.startswith(("@walk", "@mine", "@build", "@explore")):
            set_bot_action(bot_state, "@idle")
        return

    if action.startswith("@walk") and "walking" not in task:
        set_bot_action(bot_state, "@idle")
    elif action.startswith("@mine") and "mining" not in task:
        set_bot_action(bot_state, "@idle")
    elif action.startswith("@build") and "building" not in task:
        set_bot_action(bot_state, "@idle")
    elif action.startswith("@explore") and "expedition" not in task and "exploring" not in task:
        set_bot_action(bot_state, "@idle")

# ============================================================================
# LLM RESPONSE GENERATION
# ============================================================================

def generate_llm_response(npc_name, npc_state, player_name, message):
    if is_simple_greeting(message):
        options = [
            f"Hey {player_name}. I'm here and keeping watch.",
            f"Hello {player_name}. Sword is ready, eyes are open.",
            f"Hi {player_name}. I'm staying close and watching the area.",
        ]
        reply = choose_non_repeating(options, npc_state.get("recent_replies", []))
        remember_reply(npc_state, reply)
        return reply

    mood = npc_state.get("mood", "neutral")
    mood_desc = MOOD_PERSONALITY.get(mood, "")
    player_ctx = get_player_context(player_name)
    threats = npc_state.get("nearby_threats", [])
    threat_str = f"\nNearby danger: {', '.join(threats)}. Stay calm; mention it only if it matters." if threats else ""
    nearby = npc_state.get("nearby_players", [])
    nearby_str = f"\nOther players nearby: {', '.join(nearby)}." if nearby else ""
    tier = npc_state.get("equipment_tier", "iron")
    appearance_variant = npc_state.get("appearance_variant", "wanderer")
    inventory = npc_state.get("inventory", [])
    inv_slots = npc_state.get("inventory_slots", 0)
    inv_capacity = npc_state.get("inventory_capacity", 27)
    food_count = sum(item.get("count", 0) for item in inventory if item.get("food"))
    distance_from_home = npc_state.get("distance_from_home", 0)
    expedition_home = npc_state.get("expedition_home", "")
    expedition_destination = npc_state.get("expedition_destination", "")
    expedition_returning = npc_state.get("expedition_returning_home", False)
    expedition_remaining = npc_state.get("expedition_route_remaining", 0)
    inventory_preview = ", ".join(
        f"{item.get('count', 0)}x {item.get('id', 'unknown').split(':')[-1]}"
        for item in inventory[:8]
    ) or "empty"
    recent_replies = npc_state.get("recent_replies", [])
    recent_reply_text = "\n".join(f"- {line}" for line in recent_replies[-5:]) or "- Nothing recent."
    forced_action = infer_direct_action(player_name, message)

    diff = DIFFICULTY_SETTINGS.get(DIFFICULTY, DIFFICULTY_SETTINGS["medium"])

    system_prompt = f"""You are {npc_name}, an AI companion living inside a Minecraft world.

Personality: {mood_desc}{threat_str}{nearby_str}
Player context: {player_ctx}

Your current state:
- Health: {round(npc_state['health'], 1)}/20
- Position: X:{round(npc_state['x'])}, Y:{round(npc_state['y'])}, Z:{round(npc_state['z'])}
- Current task: {npc_state.get('task', 'wandering')}
- Companion role/look: {appearance_variant}
- Mood: {mood}
- Equipment tier: {tier}
- Inventory: {inv_slots}/{inv_capacity} slots used; {food_count} food item(s); items: {inventory_preview}
- Expedition: {distance_from_home} blocks from home; home: {expedition_home or 'unknown'}; destination: {expedition_destination or 'none'}; returning home: {expedition_returning}; route points left: {expedition_remaining}

You have free will. If the player gives a command, follow it. Otherwise act based on your mood.
Keep your reply to 1-2 short, natural sentences. Sound like a real Minecraft adventurer with opinions, not a customer-service bot.
Be truthful about your current task and physical situation. Never claim you are mining, building, farming, or safe when the current state says otherwise.
If your current task is escaping deep water, plainly say that you are stuck swimming or trying to reach shore.
Prefer concrete world details (boots, torches, cliffs, doors, ore, roofs, mobs) over bland phrases like "okay" or "on it."
Builders must not start builds unless the player clearly asks for one. Farmers should let their in-world farm routine handle crops and animals while idle.
Role boundaries are strict: farmers farm, miners mine, guardians patrol/protect, scouts make long expeditions and report valuable discoveries, wanderers visit nearby players and report local landmarks, builders wait for build requests.
Never suggest destroying roads, paths, fences, gates, farms, lights, chests, doors, or player-made builds.
Do not repeat your recent wording. Recent things you said:
{recent_reply_text}

Valid action commands (pick ONE):
- "@idle"                        — wander and explore naturally
- "@follow <player_name>"        — follow a specific player
- "@walk <x> <y> <z>"           — walk to exact coordinates
- "@explore <near|far|deep|return|distance>" — plan a long expedition from home; near≈1000, far≈2500, deep≈5000 blocks
- "@stop"                        — stop all movement
- "@forgive <player_name>"       — forgive an accidental player hit and stop fighting that player
- "@mine <block_name>"           — mine a block type (e.g. @mine iron_ore, @mine log)
- "@build <small|cottage|tower|hall|castle|bridge|wall|base|pen>" — build a named schematic nearby; pen is a livestock fence with a gate
- "@resume_build"                — resume and finish the currently unfinished build

Output ONLY a valid JSON object (no extra text):
{{
  "reply": "What you say in Minecraft chat",
  "action": "the command string"
}}"""

    for attempt in range(3):
        try:
            response = client.chat.completions.create(
                model=LLM_MODEL,
                messages=[
                    {"role": "system", "content": system_prompt},
                    {"role": "user",   "content": f"{player_name} says: {message}"},
                ],
                response_format={"type": "json_object"},
                temperature=diff["temperature"],
                max_tokens=120,
            )
            raw = response.choices[0].message.content
            result = json.loads(raw)

            action = forced_action or result.get("action", "@idle")
            if not is_valid_action(action):
                print(f"[ACTION] Invalid LLM action for {npc_name}: {action}")
                action = "@idle"
            if not forced_action and action.startswith("@build"):
                action = "@idle"
            if action:
                set_bot_action(npc_state, action)
                save_bot_state()

            reply = make_reply_less_repetitive(npc_name, npc_state, player_name, result.get("reply", "Hmm."))

            return reply

        except Exception as e:
            print(f"[LLM] Attempt {attempt + 1} failed: {e}")
            if attempt == 2:
                return make_reply_less_repetitive(npc_name, npc_state, player_name, "My brain glitched! Give me a moment.")


def generate_event_reply(npc_name, bot_state, event, detail, fallback_options):
    mood = bot_state.get("mood", "neutral")
    role = bot_state.get("appearance_variant", "wanderer")
    task = bot_state.get("task", "wandering")
    recent = bot_state.get("recent_replies", [])[-6:]
    recent_text = "\n".join(f"- {line}" for line in recent) or "- Nothing recent."

    event_guidance = {
        "found_diamonds": "You found diamonds. Be excited, but do not use the same catchphrase every time.",
        "build_complete": "You finished a build. Sound pleased and invite the player to inspect it.",
        "farm_creation": "You are starting a small crop row. Sound practical, not dramatic.",
        "storage_creation": "You are placing farm storage. Keep it short.",
        "low_health": "You are badly hurt. Ask for help briefly without being melodramatic.",
        "needs_food": "You are hurt and have no food. Mention the problem once, then focus on solving it.",
        "seeking_food": "You are actively looking for food. Sound resourceful, not helpless.",
        "dangerous_drop": "You avoided a dangerous drop. Make one quick natural comment.",
        "player_nearby": f"{detail} came nearby. Greet them by name without sounding identical to your last greetings.",
        "killed_mob": f"You defeated a {detail}. Keep it short and varied.",
        "expedition_start": "You are starting a long expedition. Mention the plan and that you remember home.",
        "expedition_turnaround": "You reached an expedition point and are turning back toward home. Sound confident.",
        "expedition_return": "You returned home from an expedition. Summarize it briefly.",
        "discovery": "You found something interesting while exploring. Give the detail naturally and include why it matters.",
        "wanderer_report": "You are a sociable regional traveller reporting a nearby visit or everyday landmark. Keep it warm, specific, and brief.",
        "storage_stocked": "You put useful supplies into a shared chest. Mention it briefly and warmly.",
    }
    guidance = event_guidance.get(event, f"React naturally to this event: {event} {detail}".strip())

    system_prompt = f"""You are {npc_name}, a Minecraft AI companion.
Role/look: {role}
Mood: {mood}
Current task: {task}
Event: {event}
Detail: {detail or "none"}

{guidance}
Write exactly one short in-character sentence for Minecraft chat.
Do not repeat recent wording. Recent lines:
{recent_text}

Output ONLY JSON:
{{"reply":"your sentence"}}"""

    try:
        response = client.chat.completions.create(
            model=LLM_MODEL,
            messages=[{"role": "system", "content": system_prompt}],
            response_format={"type": "json_object"},
            temperature=0.85,
            max_tokens=80,
        )
        raw = response.choices[0].message.content
        result = json.loads(raw)
        reply = result.get("reply", "").strip()
        if reply and not any(replies_are_too_similar(reply, old) for old in recent):
            remember_reply(bot_state, reply)
            return reply
    except Exception as e:
        print(f"[EVENT LLM] Falling back for {npc_name}/{event}: {e}")

    reply = choose_non_repeating(fallback_options, recent)
    remember_reply(bot_state, reply)
    return reply

# ============================================================================
# FLASK ROUTES
# ============================================================================

@app.route("/api/npc/chat", methods=["POST"])
def handle_chat():
    sync_active_bots_from_registry(remove_stale=True)
    data = request.json
    player_name = data.get("player", "Unknown")
    message = data.get("message", "").strip()

    print(f"\n[CHAT] {player_name}: {message}")

    group_action = group_action_from_message(message)
    if group_action:
        affected = set_group_action(player_name, group_action)
        if affected <= 0:
            return jsonify({"reply": "IGNORE", "speaker": "System"})

        reply = choose_non_repeating(GROUP_ACKS[group_action], [])
        update_player_interaction(player_name, message, reply)
        send_discord_message(f"**{player_name} -> AI Companions:** {message}\n**AI Companions:** {reply}")
        print(f"[GROUP]  {affected} bot(s): {group_action}")
        return jsonify({"reply": reply, "speaker": "AI Companions"})

    # Route to the bot whose name appears in the message
    addressed_bot = None
    for bot_name in active_bots:
        if bot_name.lower() in message.lower():
            addressed_bot = bot_name
            break

    if addressed_bot is None:
        print("[SKIP] No bot addressed by name.")
        return jsonify({"reply": "IGNORE", "speaker": "System"})

    direct_action = infer_direct_action(player_name, message)
    if direct_action:
        set_bot_action(active_bots[addressed_bot], direct_action, 3)
        save_bot_state()

    reply = direct_command_reply(
        addressed_bot, player_name, direct_action, active_bots[addressed_bot]
    ) if direct_action else None
    if not reply:
        reply = generate_llm_response(addressed_bot, active_bots[addressed_bot], player_name, message)
    update_player_interaction(player_name, message, reply)
    send_discord_message(f"**{player_name} -> {addressed_bot}:** {message}\n**{addressed_bot}:** {reply}")

    print(f"[LLM]    {addressed_bot}: {reply}")
    print(f"[ACTION] {addressed_bot}: {active_bots[addressed_bot]['current_action']}")

    return jsonify({"reply": reply, "speaker": addressed_bot})


@app.route("/api/npc/action", methods=["POST"])
def handle_action():
    data = request.json
    npc_name = data.get("npcName", "Bartholomew")
    request_id = data.get("requestId", 0)

    if npc_name not in active_bots:
        active_bots[npc_name] = create_default_bot_state()
        print(f"[SYSTEM] Registered new bot: {npc_name}")

    bot = active_bots[npc_name]
    bot.setdefault("recent_replies", [])
    bot.setdefault("recent_reply_norms", [])
    bot.setdefault("recent_event_replies", {})
    bot.setdefault("build_cooldown_ticks", 0)
    bot.setdefault("explore_cooldown_ticks", 0)
    bot.setdefault("action_grace_ticks", 0)
    bot["health"]          = data.get("health", 20.0)
    bot["x"]               = data.get("x", 0)
    bot["y"]               = data.get("y", 0)
    bot["z"]               = data.get("z", 0)
    bot["task"]            = data.get("task", bot["task"])
    bot["appearance_variant"] = data.get("appearanceVariant", bot.get("appearance_variant", "wanderer"))
    bot["reported_action"] = data.get("activeAction", bot.get("reported_action", "@idle"))
    bot["nearby_players"]  = data.get("nearbyPlayers", [])
    bot["nearby_threats"]  = data.get("nearbyThreats", [])
    bot["equipment_tier"]  = data.get("equipmentTier", bot.get("equipment_tier", "iron"))
    bot["inventory"]       = data.get("inventory", [])
    bot["inventory_slots"] = data.get("inventorySlotsUsed", 0)
    bot["inventory_capacity"] = data.get("inventoryCapacity", 27)
    bot["distance_from_home"] = data.get("distanceFromHome", bot.get("distance_from_home", 0))
    bot["expedition_home"] = data.get("expeditionHome", bot.get("expedition_home", ""))
    bot["expedition_destination"] = data.get("expeditionDestination", bot.get("expedition_destination", ""))
    bot["expedition_returning_home"] = data.get("expeditionReturningHome", bot.get("expedition_returning_home", False))
    bot["expedition_route_remaining"] = data.get("expeditionRouteRemaining", bot.get("expedition_route_remaining", 0))
    bot["build_blocks_total"] = data.get("buildBlocksTotal", bot.get("build_blocks_total", 0))
    bot["build_blocks_remaining"] = data.get("buildBlocksRemaining", bot.get("build_blocks_remaining", 0))
    bot["build_progress_percent"] = data.get("buildProgressPercent", bot.get("build_progress_percent", 100))
    if bot["build_cooldown_ticks"] > 0:
        bot["build_cooldown_ticks"] -= 1
    if bot["explore_cooldown_ticks"] > 0:
        bot["explore_cooldown_ticks"] -= 1
    if bot.get("current_action", "").startswith("@build") and bot["build_cooldown_ticks"] > 0 and bot.get("action_grace_ticks", 0) <= 0:
        set_bot_action(bot, "@idle")
    if bot.get("current_action", "").startswith("@explore") and bot["explore_cooldown_ticks"] > 0 and bot["expedition_route_remaining"] == 0:
        set_bot_action(bot, "@idle")
    reconcile_action_with_task(bot)

    update_mood(bot)
    maybe_proactive_action(npc_name, bot)
    action_to_return = bot["current_action"]
    if action_to_return.startswith("@build") or action_to_return == "@resume_build":
        set_bot_action(bot, "@idle")
    save_bot_state()

    return jsonify({"requestId": request_id, "action": action_to_return, "mood": bot["mood"]})


@app.route("/api/npc/event", methods=["POST"])
def handle_event():
    """Receive in-game events and generate a short NPC broadcast reply."""
    data = request.json
    npc_name = data.get("npcName", "Bot")
    event    = data.get("event", "")
    detail   = data.get("detail", "")

    EVENT_REPLIES = {
        "found_diamonds": [
            "DIAMONDS! I found diamonds!!",
            "Found diamonds. I am trying to look calm and failing.",
            "Diamonds! Come see this!",
            "Blue sparkles in the wall. That is usually the good kind of trouble.",
            "I found the shiny stuff. Please admire my excellent digging choices.",
        ],
        "build_complete": [
            "The house is done! Come have a look!",
            "Construction complete! Not bad for one day's work.",
            "Built it just like I planned!",
            "House is finished. I only bonked my head on the roof twice.",
            "Done. It has walls, a roof, and an acceptable amount of dignity.",
        ],
        "farm_creation": [
            "I am starting a small crop row here.",
            "This spot can support a tidy little farm.",
            "I found workable soil and water. Time for crops.",
        ],
        "storage_creation": [
            "I am setting up a chest for farm supplies.",
            "Storage first, then fewer pockets full of wheat.",
            "I found a safe spot for farm storage.",
        ],
        "low_health": [
            "I'm badly hurt. I could use backup.",
            "Getting beaten up over here!",
            "I am too low on health to pretend this is fine.",
            "Little help? My heroic confidence is leaking.",
            "I need space and a second to recover.",
        ],
        "needs_food": [
            "I am hurt and out of food, so I am switching to survival mode.",
            "No food left. I am going to look for something edible.",
            "I need food, but I am not going to just stand here whining about it.",
            "Food inventory is empty. Time to fix that.",
        ],
        "seeking_food": [
            "I am looking for food now. Less complaining, more problem solving.",
            "Going to find something edible before I get flattened.",
            "I spotted a possible food source and I am moving for it.",
            "I am handling the food problem. Probably with a sword.",
            "I need healing, so I am doing the sensible snack hunt.",
        ],
        "dangerous_drop": [
            "Careful, that's a nasty drop!",
            "Nope. That cliff has bad ideas.",
            "I nearly walked off an edge. Very rude terrain.",
            "I saw the drop and chose life.",
            "That ledge can keep its invitation.",
        ],
        "threat_warning": [
            f"Heads up: {detail}.",
            f"Watch it, {detail}. Stay sharp.",
            f"Bad news nearby: {detail}.",
            f"I see trouble: {detail}.",
            f"Careful. {detail} and I do not like the angle.",
            f"Warning call: {detail}.",
        ],
        "player_nearby": [
            f"Oh! {detail} is here! Hey!",
            f"Good to see you, {detail}!",
            f"Hey {detail}! What are you up to?",
            f"{detail}, there you are.",
            f"Hi {detail}. I was absolutely being productive.",
        ],
        "killed_mob": [
            f"Took care of that {detail}!",
            f"That {detail} won't bother us anymore.",
            "Enemy down!",
            f"{detail} handled. I accept polite applause.",
            "Threat cleared. Back to business.",
        ],
        "expedition_start": [
            f"Starting a real expedition. I marked home and I am heading out: {detail}.",
            f"I have my home point. Time to see what is out there: {detail}.",
            "Going long-range. I know the way back.",
            "Expedition started. I will report anything worth seeing.",
        ],
        "expedition_turnaround": [
            "Reached the outer point. Turning back toward home now.",
            "That is far enough for this leg. I am bringing myself home.",
            "I found my limit marker. Route home is active.",
            f"Turning around: {detail}.",
        ],
        "expedition_return": [
            "I made it back from the expedition.",
            "Back home. I kept the route in my head and everything.",
            "Expedition complete. I am back where I started.",
            "Returned from scouting. My boots have opinions.",
        ],
        "discovery": [
            f"I found something interesting: {detail}.",
            f"Discovery report: {detail}.",
            f"Mark this spot: {detail}.",
            f"I found a point of interest: {detail}.",
        ],
        "wanderer_report": [
            f"Local travel note: {detail}.",
            f"I made the rounds and found {detail}.",
            f"Something worth knowing nearby: {detail}.",
            f"A small report from the road: {detail}.",
        ],
        "storage_stocked": [
            "I stocked the chest. Farm goods are there for anyone who needs them.",
            "Supplies are in the chest now. Help yourselves.",
            "Chest has fresh farm goods in it. Try to leave me a little dignity and maybe a carrot.",
            "I put the useful harvest away. Shared chest is ready.",
        ],
    }

    options = EVENT_REPLIES.get(event, [f"Interesting... {detail}"])
    bot = active_bots.setdefault(npc_name, create_default_bot_state())
    if event == "build_complete":
        set_bot_action(bot, "@idle")
        bot["build_cooldown_ticks"] = 240
    if event == "expedition_return":
        set_bot_action(bot, "@idle")
        bot["explore_cooldown_ticks"] = 600
    if not should_emit_event_chat(event):
        save_bot_state()
        print(f"[EVENT] {npc_name} — '{event}' suppressed by {CHAT_LEVEL} chat level")
        return jsonify({"reply": "IGNORE", "speaker": npc_name})
    event_history = bot.setdefault("recent_event_replies", {}).setdefault(event, [])
    reply = generate_event_reply(npc_name, bot, event, detail, options)
    if any(replies_are_too_similar(reply, old) for old in event_history):
        reply = choose_non_repeating(options, event_history + bot.get("recent_replies", []))
        remember_reply(bot, reply)
    event_history.append(reply)
    bot["recent_event_replies"][event] = event_history[-MAX_RECENT_EVENT_REPLIES:]
    save_bot_state()
    print(f"[EVENT] {npc_name} — '{event}': {reply}")

    return jsonify({"reply": reply, "speaker": npc_name})


# ============================================================================
# DASHBOARD
# ============================================================================

DASHBOARD_HTML = """<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>AI Companion Dashboard</title>
<style>
  * { box-sizing: border-box; }
  body {
    margin: 0;
    background: #f4f5f7;
    color: #202124;
    font-family: Inter, Segoe UI, Roboto, Arial, sans-serif;
    font-size: 14px;
  }
  header {
    background: #ffffff;
    border-bottom: 1px solid #d7dbe2;
    padding: 14px 22px;
    display: flex;
    align-items: center;
    gap: 14px;
    flex-wrap: wrap;
    position: sticky;
    top: 0;
    z-index: 5;
  }
  h1 { font-size: 18px; margin: 0; font-weight: 700; letter-spacing: 0; }
  main { padding: 16px 20px 24px; max-width: 1500px; margin: 0 auto; }
  .badge {
    border: 1px solid #d7dbe2;
    background: #f8f9fb;
    color: #4b5563;
    border-radius: 6px;
    padding: 5px 10px;
    font-size: 12px;
    white-space: nowrap;
  }
  .controls { margin-left: auto; display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
  label { color: #5f6673; font-size: 12px; }
  select, button, input {
    height: 32px;
    background: #ffffff;
    color: #202124;
    border: 1px solid #c8ced8;
    border-radius: 6px;
    padding: 0 10px;
    font: inherit;
    font-size: 13px;
  }
  button { cursor: pointer; background: #1f6feb; border-color: #1f6feb; color: #ffffff; font-weight: 600; }
  button:hover { background: #185abc; }
  button.secondary { background: #ffffff; color: #374151; border-color: #c8ced8; }
  button.secondary:hover { background: #f3f4f6; }
  button.danger { background: #b42318; border-color: #b42318; }
  button.danger:hover { background: #8f1d13; }
  button.small { height: 28px; padding: 0 8px; font-size: 12px; }
  input { min-width: 130px; }
  .control-message { min-width: 120px; text-align: center; }
  .control-message.bad { border-color: #e56b6f; background: #fff0f1; color: #9c1c28; }
  .control-message.good { border-color: #28a745; background: #effaf2; color: #1f7a38; }
  .status-bar {
    margin: 0 0 14px;
    color: #4b5563;
    min-height: 22px;
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 8px;
  }
  .status-pill {
    background: #ffffff;
    border: 1px solid #d7dbe2;
    border-radius: 6px;
    padding: 4px 8px;
    font-size: 12px;
  }
  .status-pill.warn { border-color: #f2b84b; background: #fff8e8; color: #7a4b00; }
  .status-pill.bad { border-color: #e56b6f; background: #fff0f1; color: #9c1c28; }
  .summary {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
    gap: 10px;
    margin-bottom: 16px;
  }
  .stat {
    background: #ffffff;
    border: 1px solid #d7dbe2;
    border-radius: 8px;
    padding: 12px;
    min-height: 76px;
  }
  .stat .label { color: #6b7280; font-size: 12px; margin-bottom: 7px; }
  .stat .value { font-size: 24px; font-weight: 750; line-height: 1; }
  .stat .sub { color: #6b7280; font-size: 12px; margin-top: 8px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .layout {
    display: grid;
    grid-template-columns: minmax(0, 1fr) 340px;
    gap: 16px;
    align-items: start;
  }
  .panel-title {
    display: flex;
    justify-content: space-between;
    align-items: center;
    color: #4b5563;
    font-size: 12px;
    text-transform: uppercase;
    margin: 0 0 8px;
  }
  .grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(310px, 1fr)); gap: 12px; }
  .card {
    background: #ffffff;
    border: 1px solid #d7dbe2;
    border-radius: 8px;
    padding: 14px;
    min-width: 0;
  }
  .card.danger { border-left: 4px solid #d64545; }
  .card.caution { border-left: 4px solid #d98c21; }
  .card.good { border-left: 4px solid #28a745; }
  .card-header { display: flex; align-items: center; gap: 8px; margin-bottom: 10px; min-width: 0; }
  .dot { width: 9px; height: 9px; border-radius: 50%; background: #28a745; flex: 0 0 auto; }
  .card-name { font-size: 16px; font-weight: 750; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .chip {
    border: 1px solid #d7dbe2;
    background: #f8f9fb;
    color: #4b5563;
    border-radius: 6px;
    padding: 3px 7px;
    font-size: 11px;
    white-space: nowrap;
  }
  .mood { margin-left: auto; }
  .card-actions { margin-left: 0; display: flex; justify-content: flex-end; }
  .health-bar { height: 8px; background: #edf0f4; border-radius: 999px; overflow: hidden; margin: 4px 0 10px; }
  .health-fill { height: 100%; transition: width .35s ease; }
  .health-high { background: #28a745; }
  .health-med { background: #d98c21; }
  .health-low { background: #d64545; }
  .rows { display: grid; grid-template-columns: 1fr 1fr; gap: 6px 12px; margin-bottom: 10px; }
  .row { min-width: 0; }
  .row span { display: block; color: #6b7280; font-size: 11px; margin-bottom: 2px; }
  .row strong { display: block; font-size: 13px; font-weight: 650; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .task {
    border-top: 1px solid #eef0f3;
    padding-top: 10px;
    display: grid;
    gap: 6px;
  }
  .action {
    font-family: Consolas, Menlo, monospace;
    background: #f7f8fa;
    border: 1px solid #e1e5eb;
    border-radius: 6px;
    padding: 6px 8px;
    color: #315b85;
    overflow-wrap: anywhere;
  }
  .line { color: #4b5563; overflow-wrap: anywhere; }
  .threat { color: #a31926; font-weight: 700; }
  .players { color: #185abc; }
  .side { display: grid; gap: 12px; }
  .side-panel {
    background: #ffffff;
    border: 1px solid #d7dbe2;
    border-radius: 8px;
    padding: 14px;
  }
  .list { display: grid; gap: 8px; }
  .list-row {
    display: grid;
    gap: 2px;
    border-bottom: 1px solid #eef0f3;
    padding-bottom: 8px;
    min-width: 0;
  }
  .list-row:last-child { border-bottom: 0; padding-bottom: 0; }
  .list-row strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .muted { color: #6b7280; font-size: 12px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .empty { color: #7b8494; padding: 18px 4px; font-style: italic; }
  .schematic-editor {
    margin-top: 18px;
    background: #ffffff;
    border: 1px solid #d7dbe2;
    border-radius: 8px;
    padding: 14px;
  }
  .editor-toolbar { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; margin-bottom: 12px; }
  .editor-workspace { display: grid; grid-template-columns: minmax(320px, 620px) minmax(230px, 1fr); gap: 16px; }
  .blueprint-grid {
    display: grid;
    grid-template-columns: repeat(15, minmax(20px, 1fr));
    border: 1px solid #9da7b5;
    background: #9da7b5;
    gap: 1px;
    user-select: none;
  }
  .blueprint-cell {
    aspect-ratio: 1;
    border: 0;
    min-width: 0;
    height: auto;
    padding: 0;
    border-radius: 0;
    background: #f8fafc;
    color: #374151;
    font-size: 9px;
    overflow: hidden;
  }
  .blueprint-cell:hover { outline: 2px solid #2563eb; outline-offset: -2px; filter: brightness(.96); }
  .blueprint-cell.origin { box-shadow: inset 0 0 0 2px #dc2626; }
  .editor-help { color: #5f6977; font-size: 13px; line-height: 1.55; }
  .palette-preview { width: 18px; height: 18px; display: inline-block; border: 1px solid #aab2bf; vertical-align: middle; }
  .schematic-list { margin-top: 12px; display: flex; flex-wrap: wrap; gap: 7px; }
  footer { color: #7b8494; font-size: 12px; padding: 14px 0 0; text-align: center; }
  @media (max-width: 980px) {
    .controls { margin-left: 0; }
    .layout { grid-template-columns: 1fr; }
    .editor-workspace { grid-template-columns: 1fr; }
    header { position: static; }
  }
</style>
</head>
<body>
<header>
  <h1>AI Companion Dashboard</h1>
  <span class="badge" id="bot-count">loading</span>
  <span class="badge" id="last-update">waiting</span>
  <div class="controls">
    <label for="difficulty-select">Difficulty</label>
    <select id="difficulty-select">
      <option value="easy">Easy</option>
      <option value="medium">Medium</option>
      <option value="hard">Hard</option>
    </select>
    <button onclick="applyDifficulty()">Apply</button>
    <label for="chat-level-select">Chit chat</label>
    <select id="chat-level-select">
      <option value="low">Low</option>
      <option value="medium">Medium</option>
      <option value="high">High</option>
    </select>
    <button onclick="applyChatLevel()">Apply</button>
    <label for="bot-name-input">Bot</label>
    <input id="bot-name-input" type="text" maxlength="24" placeholder="Name optional">
    <select id="bot-role-select" title="Starting role">
      <option value="">Balanced role</option>
      <option value="wanderer">Wanderer</option>
      <option value="miner">Miner</option>
      <option value="guardian">Guardian</option>
      <option value="builder">Builder</option>
      <option value="scout">Scout</option>
      <option value="farmer">Farmer</option>
    </select>
    <button onclick="addBot()">+ Add Bot</button>
    <button class="secondary" onclick="clearBots()">Clear All</button>
    <span class="badge control-message" id="control-message">Ready</span>
  </div>
</header>
<main>
  <div class="status-bar" id="status-bar"><span class="status-pill">Connecting...</span></div>
  <section class="summary" id="summary"></section>
  <div class="layout">
    <section>
      <div class="panel-title"><span>Companions</span><span id="refresh-note">Auto refresh: 3s</span></div>
      <div class="grid" id="companion-grid"></div>
    </section>
    <aside class="side">
      <section class="side-panel">
        <div class="panel-title"><span>Threat Watch</span></div>
        <div class="list" id="threat-watch"></div>
      </section>
      <section class="side-panel">
        <div class="panel-title"><span>Player Memory</span></div>
        <div class="list" id="player-memory"></div>
      </section>
    </aside>
  </div>
  <section class="schematic-editor">
    <div class="panel-title"><span>Visual Builder Schematic Editor</span><span id="schematic-status">New design</span></div>
    <div class="editor-toolbar">
      <label>Name <input id="schematic-name" maxlength="40" placeholder="cozy_house"></label>
      <label>Layer Y
        <select id="schematic-layer" onchange="setSchematicLayer(Number(this.value))"></select>
      </label>
      <label>Block
        <select id="schematic-block" onchange="renderSchematicGrid()">
          <option value="minecraft:oak_planks">Oak planks</option>
          <option value="minecraft:stone_bricks">Stone bricks</option>
          <option value="minecraft:cobblestone">Cobblestone</option>
          <option value="minecraft:spruce_planks">Spruce planks</option>
          <option value="minecraft:glass">Glass</option>
          <option value="minecraft:oak_log">Oak log</option>
          <option value="minecraft:oak_fence">Oak fence</option>
          <option value="minecraft:oak_stairs">Oak stairs</option>
          <option value="minecraft:oak_door">Oak door</option>
          <option value="minecraft:ladder">Ladder</option>
          <option value="minecraft:oak_trapdoor">Oak trapdoor</option>
          <option value="minecraft:torch">Torch</option>
          <option value="minecraft:wall_torch">Wall torch</option>
          <option value="minecraft:red_bed">Red bed</option>
          <option value="minecraft:chest">Chest</option>
          <option value="minecraft:furnace">Furnace</option>
          <option value="minecraft:crafting_table">Crafting table</option>
          <option value="minecraft:air">Eraser</option>
        </select>
      </label>
      <label>Facing
        <select id="schematic-facing">
          <option value="north">North</option>
          <option value="east">East</option>
          <option value="south">South</option>
          <option value="west">West</option>
        </select>
      </label>
      <label>Half
        <select id="schematic-half">
          <option value="bottom">Bottom / lower</option>
          <option value="top">Top / upper</option>
        </select>
      </label>
      <button onclick="fillSchematicLayer()">Fill layer</button>
      <button class="secondary" onclick="clearSchematicLayer()">Clear layer</button>
      <button class="secondary" onclick="newSchematic()">New</button>
      <button onclick="saveSchematic()">Save schematic</button>
    </div>
    <div class="editor-workspace">
      <div id="blueprint-grid" class="blueprint-grid"></div>
      <div class="editor-help">
        <p><strong>Paint blocks by clicking or dragging.</strong> The red-outlined square is the build center at X=0, Z=0.</p>
        <p>Use Y=-1 for foundations/floors, Y=0 upward for walls and roofs. Choose Facing for stairs, doors, ladders, trapdoors, and wall torches.</p>
        <p>Doors automatically add their upper half. Beds automatically add their head block in the facing direction. “Half” controls stairs and trapdoors.</p>
        <p>After saving, say: <code>Builder, build cozy house</code>. Names are converted to lowercase with underscores.</p>
        <p><strong>Current:</strong> <span id="schematic-count">0 blocks</span></p>
        <div class="schematic-list" id="schematic-list"></div>
      </div>
    </div>
  </section>
  <footer>localhost:8080/dashboard</footer>
</main>

<script>
const ROLE_LABELS = { miner:'Miner', guardian:'Guardian', builder:'Builder', scout:'Scout', farmer:'Farmer', wanderer:'Wanderer' };
const MOOD_TONES = {
  happy:'#9a6700', excited:'#b93815', neutral:'#4b5563',
  tired:'#6b7280', curious:'#116d7e', cautious:'#6f5f00', scared:'#a31926'
};
const SCHEMATIC_SIZE = 15;
const BLOCK_COLORS = {
  'minecraft:oak_planks':'#c89b5b', 'minecraft:spruce_planks':'#75502d',
  'minecraft:stone_bricks':'#858585', 'minecraft:cobblestone':'#6f7378',
  'minecraft:glass':'#bde9f2', 'minecraft:oak_log':'#8b6438',
  'minecraft:oak_fence':'#b9874c', 'minecraft:oak_stairs':'#d3a565',
  'minecraft:oak_door':'#a76f35', 'minecraft:ladder':'#c29455',
  'minecraft:oak_trapdoor':'#9f713d', 'minecraft:torch':'#f5b942',
  'minecraft:wall_torch':'#f5b942', 'minecraft:red_bed':'#c83f49',
  'minecraft:chest':'#ad712f', 'minecraft:furnace':'#686868',
  'minecraft:crafting_table':'#956337'
};
let schematicBlocks = {};
let schematicLayer = 0;
let schematicPainting = false;
let schematicEraseMode = false;

function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>"']/g, c => ({
    '&':'&amp;', '<':'&lt;', '>':'&gt;', '"':'&quot;', "'":'&#39;'
  }[c]));
}

function schematicKey(x, y, z) { return `${x},${y},${z}`; }
function schematicBlockId(value) { return typeof value === 'string' ? value : value?.block; }
function schematicBlockProperties(value) { return typeof value === 'string' ? {} : (value?.properties || {}); }

function selectedSchematicBlock() {
  const block = document.getElementById('schematic-block').value;
  const facing = document.getElementById('schematic-facing').value;
  const halfChoice = document.getElementById('schematic-half').value;
  const properties = {};
  if (['minecraft:oak_stairs', 'minecraft:oak_door', 'minecraft:ladder',
       'minecraft:oak_trapdoor', 'minecraft:wall_torch', 'minecraft:red_bed',
       'minecraft:chest', 'minecraft:furnace'].includes(block)) properties.facing = facing;
  if (block === 'minecraft:oak_stairs' || block === 'minecraft:oak_trapdoor') properties.half = halfChoice;
  if (block === 'minecraft:oak_door') properties.half = 'lower';
  if (block === 'minecraft:red_bed') properties.part = 'foot';
  return {block, properties};
}

function offsetForFacing(facing) {
  if (facing === 'north') return [0, -1];
  if (facing === 'south') return [0, 1];
  if (facing === 'east') return [1, 0];
  return [-1, 0];
}

function setupSchematicLayers() {
  const select = document.getElementById('schematic-layer');
  select.innerHTML = '';
  for (let y = -1; y <= 9; y++) {
    const option = document.createElement('option');
    option.value = y;
    option.textContent = y === -1 ? 'Foundation (-1)' : `Layer ${y}`;
    select.appendChild(option);
  }
  select.value = String(schematicLayer);
}

function setSchematicLayer(y) {
  schematicLayer = y;
  renderSchematicGrid();
}

function paintSchematicCell(x, z, erase = false, cell = null) {
  const selected = selectedSchematicBlock();
  const block = erase || selected.block === 'minecraft:air' ? null : selected;
  const key = schematicKey(x, schematicLayer, z);
  if (block) schematicBlocks[key] = block;
  else delete schematicBlocks[key];
  if (block?.block === 'minecraft:oak_door') {
    schematicBlocks[schematicKey(x, schematicLayer + 1, z)] = {
      block: block.block,
      properties: {...block.properties, half: 'upper'}
    };
  }
  if (block?.block === 'minecraft:red_bed') {
    const [dx, dz] = offsetForFacing(block.properties.facing);
    const headX = x + dx;
    const headZ = z + dz;
    const half = Math.floor(SCHEMATIC_SIZE / 2);
    if (Math.abs(headX) <= half && Math.abs(headZ) <= half) {
      schematicBlocks[schematicKey(headX, schematicLayer, headZ)] = {
        block: block.block,
        properties: {...block.properties, part: 'head'}
      };
    }
  }
  if (cell) {
    const blockId = schematicBlockId(block);
    cell.style.background = blockId ? (BLOCK_COLORS[blockId] || '#a78bfa') : '#f8fafc';
    cell.textContent = blockId ? blockId.split(':').pop().split('_').map(w => w[0]).join('').slice(0, 3) : '';
    cell.title = `X ${x}, Y ${schematicLayer}, Z ${z}${blockId ? ` — ${blockId}` : ''}`;
  }
  document.getElementById('schematic-count').textContent = `${Object.keys(schematicBlocks).length} blocks`;
}

function renderSchematicGrid() {
  const grid = document.getElementById('blueprint-grid');
  grid.innerHTML = '';
  const half = Math.floor(SCHEMATIC_SIZE / 2);
  for (let z = -half; z <= half; z++) {
    for (let x = -half; x <= half; x++) {
      const key = schematicKey(x, schematicLayer, z);
      const block = schematicBlocks[key];
      const blockId = schematicBlockId(block);
      const cell = document.createElement('button');
      cell.type = 'button';
      cell.className = `blueprint-cell${x === 0 && z === 0 ? ' origin' : ''}`;
      cell.title = `X ${x}, Y ${schematicLayer}, Z ${z}${blockId ? ` — ${blockId}` : ''}`;
      cell.style.background = blockId ? (BLOCK_COLORS[blockId] || '#a78bfa') : '#f8fafc';
      cell.textContent = blockId ? blockId.split(':').pop().split('_').map(w => w[0]).join('').slice(0, 3) : '';
      cell.onmousedown = event => {
        event.preventDefault();
        schematicPainting = true;
        schematicEraseMode = event.button === 2 || document.getElementById('schematic-block').value === 'minecraft:air';
        paintSchematicCell(x, z, schematicEraseMode, cell);
      };
      cell.onmouseenter = () => {
        if (schematicPainting) paintSchematicCell(x, z, schematicEraseMode, cell);
      };
      cell.oncontextmenu = event => event.preventDefault();
      grid.appendChild(cell);
    }
  }
  document.getElementById('schematic-count').textContent = `${Object.keys(schematicBlocks).length} blocks`;
}

function fillSchematicLayer() {
  const block = selectedSchematicBlock();
  if (block.block === 'minecraft:air') return clearSchematicLayer();
  const half = Math.floor(SCHEMATIC_SIZE / 2);
  for (let z = -half; z <= half; z++) {
    for (let x = -half; x <= half; x++) schematicBlocks[schematicKey(x, schematicLayer, z)] = structuredClone(block);
  }
  renderSchematicGrid();
}

function clearSchematicLayer() {
  for (const key of Object.keys(schematicBlocks)) {
    if (Number(key.split(',')[1]) === schematicLayer) delete schematicBlocks[key];
  }
  renderSchematicGrid();
}

function newSchematic() {
  schematicBlocks = {};
  schematicLayer = 0;
  document.getElementById('schematic-name').value = '';
  document.getElementById('schematic-layer').value = '0';
  document.getElementById('schematic-status').textContent = 'New design';
  renderSchematicGrid();
}

function schematicPayload() {
  return {
    name: document.getElementById('schematic-name').value,
    blocks: Object.entries(schematicBlocks).map(([key, value]) => {
      const [x, y, z] = key.split(',').map(Number);
      return {x, y, z, block: schematicBlockId(value), properties: schematicBlockProperties(value)};
    })
  };
}

function saveSchematic() {
  const payload = schematicPayload();
  fetch('/api/schematics', {
    method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(payload)
  }).then(async response => {
    const data = await response.json();
    if (!response.ok) throw new Error(data.error || 'Could not save schematic');
    document.getElementById('schematic-name').value = data.name;
    document.getElementById('schematic-status').textContent = `Saved ${data.name}`;
    loadSchematicList();
  }).catch(error => document.getElementById('schematic-status').textContent = error.message);
}

function loadSchematic(name) {
  fetch(`/api/schematics/${encodeURIComponent(name)}`).then(r => r.json()).then(data => {
    schematicBlocks = {};
    for (const entry of data.blocks || []) {
      schematicBlocks[schematicKey(entry.x, entry.y, entry.z)] = {
        block: entry.block, properties: entry.properties || {}
      };
    }
    document.getElementById('schematic-name').value = data.name || name;
    document.getElementById('schematic-status').textContent = `Editing ${data.name || name}`;
    renderSchematicGrid();
  });
}

function deleteSchematic(name) {
  if (!confirm(`Delete schematic ${name}?`)) return;
  fetch(`/api/schematics/${encodeURIComponent(name)}`, {method:'DELETE'}).then(loadSchematicList);
}

function loadSchematicList() {
  fetch('/api/schematics').then(r => r.json()).then(data => {
    const list = document.getElementById('schematic-list');
    list.innerHTML = (data.schematics || []).map(item =>
      `<span><button class="secondary small" onclick="loadSchematic(${jsLiteral(item.name)})">${escapeHtml(item.name)} (${item.block_count})</button>` +
      `<button class="danger small" onclick="deleteSchematic(${jsLiteral(item.name)})">×</button></span>`
    ).join('') || '<span class="muted">No custom schematics saved yet.</span>';
  });
}

function jsLiteral(value) {
  return escapeHtml(JSON.stringify(String(value ?? '')));
}

function hpClass(hp) {
  return hp > 14 ? 'health-high' : hp > 7 ? 'health-med' : 'health-low';
}

function cardClass(bot) {
  const hp = bot.health ?? 20;
  const threats = bot.nearby_threats || [];
  if (threats.length || hp < 7) return 'danger';
  if (hp < 14 || (bot.mood || '') === 'cautious') return 'caution';
  return 'good';
}

function shortItemName(id) {
  const raw = String(id || '').split(':').pop().replaceAll('_', ' ');
  return raw.length > 22 ? raw.slice(0, 21) + '...' : raw;
}

function inventorySummary(bot) {
  const items = Array.isArray(bot.inventory) ? bot.inventory : [];
  if (!items.length) return `${bot.inventory_slots || 0}/${bot.inventory_capacity || 27} slots`;
  return items.slice(0, 3).map(i => `${i.count || 1} ${shortItemName(i.id)}`).join(', ');
}

function renderSummary(names, bots) {
  const active = names.filter(n => (bots[n].current_action || '@idle') !== '@idle').length;
  const threats = names.filter(n => (bots[n].nearby_threats || []).length).length;
  const low = names.filter(n => (bots[n].health ?? 20) < 8).length;
  const exploring = names.filter(n => String(bots[n].current_action || '').startsWith('@explore')).length;
  return [
    ['Online', names.length, `${active} active`],
    ['Threats', threats, threats ? 'needs attention' : 'clear'],
    ['Low Health', low, low ? 'recovering or hungry' : 'stable'],
    ['Exploring', exploring, exploring ? 'routes active' : 'standing by']
  ].map(([label, value, sub]) => `<div class="stat"><div class="label">${label}</div><div class="value">${value}</div><div class="sub">${sub}</div></div>`).join('');
}

function renderCard(name, bot) {
  const hp = Math.max(0, Math.min(20, bot.health ?? 0));
  const pct = (hp / 20 * 100).toFixed(1);
  const mood = bot.mood || 'neutral';
  const role = bot.appearance_variant || 'wanderer';
  const pos = `${Math.round(bot.x || 0)}, ${Math.round(bot.y || 0)}, ${Math.round(bot.z || 0)}`;
  const threats = [...new Set(bot.nearby_threats || [])];
  const nearby = [...new Set(bot.nearby_players || [])];
  const dist = Math.round(bot.distance_from_home || 0);
  const action = bot.reported_action || bot.current_action || '@idle';
  const task = bot.task || (action === '@idle' ? 'standing by' : 'working');

  return `<article class="card ${cardClass(bot)}">
    <div class="card-header">
      <span class="dot"></span>
      <span class="card-name">${escapeHtml(name)}</span>
      <span class="chip">${escapeHtml(ROLE_LABELS[role] || role)}</span>
      <span class="chip mood" style="color:${MOOD_TONES[mood] || '#4b5563'}">${escapeHtml(mood)}</span>
    </div>
    <div class="health-bar"><div class="health-fill ${hpClass(hp)}" style="width:${pct}%"></div></div>
    <div class="rows">
      <div class="row"><span>Health</span><strong>${hp.toFixed(1)}/20</strong></div>
      <div class="row"><span>Position</span><strong>${escapeHtml(pos)}</strong></div>
      <div class="row"><span>Equipment</span><strong>${escapeHtml(bot.equipment_tier || 'iron')}</strong></div>
      <div class="row"><span>Inventory</span><strong title="${escapeHtml(inventorySummary(bot))}">${escapeHtml(inventorySummary(bot))}</strong></div>
      <div class="row"><span>From Home</span><strong>${dist} blocks</strong></div>
      <div class="row"><span>Mode</span><strong>${escapeHtml(task)}</strong></div>
    </div>
    <div class="task">
      <div class="action">${escapeHtml(action)}</div>
      ${threats.length ? `<div class="line threat">Threats: ${escapeHtml(threats.join(', '))}</div>` : ''}
      ${nearby.length ? `<div class="line players">Nearby: ${escapeHtml(nearby.join(', '))}</div>` : ''}
      ${bot.expedition_destination ? `<div class="line">Destination: ${escapeHtml(bot.expedition_destination)}</div>` : ''}
      <div class="card-actions"><button class="danger small" onclick="removeBot(${jsLiteral(name)})">- Remove</button></div>
    </div>
  </article>`;
}

function renderThreatWatch(names, bots) {
  const rows = [];
  for (const name of names) {
    const threats = [...new Set(bots[name].nearby_threats || [])];
    if (!threats.length) continue;
    rows.push(`<div class="list-row"><strong>${escapeHtml(name)}</strong><span class="muted">${escapeHtml(threats.join(', '))}</span></div>`);
  }
  return rows.length ? rows.join('') : '<div class="empty">No nearby threats reported.</div>';
}

function renderPlayerMemory(players) {
  const rows = Object.entries(players || {})
    .sort((a, b) => (b[1].interaction_count || 0) - (a[1].interaction_count || 0))
    .slice(0, 12)
    .map(([name, info]) => {
      const history = Array.isArray(info.chat_history) ? info.chat_history : [];
      const last = history.length ? history[history.length - 1].player_said : 'No recent message';
      return `<div class="list-row">
        <strong>${escapeHtml(name)}</strong>
        <span class="muted">${info.interaction_count || 0} chats - ${(info.last_seen || '').slice(0, 19)}</span>
        <span class="muted" title="${escapeHtml(last)}">${escapeHtml(last)}</span>
      </div>`;
    });
  return rows.length ? rows.join('') : '<div class="empty">No players in memory yet.</div>';
}

function renderStatus(names, bots) {
  const inDanger = names.filter(n => (bots[n].nearby_threats || []).length);
  const injured = names.filter(n => (bots[n].health ?? 20) < 8);
  const parts = [`<span class="status-pill">${names.length} companion${names.length === 1 ? '' : 's'} online</span>`];
  if (inDanger.length) parts.push(`<span class="status-pill bad">Threats: ${escapeHtml(inDanger.join(', '))}</span>`);
  if (injured.length) parts.push(`<span class="status-pill warn">Low health: ${escapeHtml(injured.join(', '))}</span>`);
  if (!inDanger.length && !injured.length) parts.push('<span class="status-pill">All systems calm</span>');
  return parts.join('');
}

function refresh() {
  fetch('/api/dashboard')
    .then(r => r.json())
    .then(data => {
      const bots = data.bots || {};
      const names = Object.keys(bots).sort();
      document.getElementById('bot-count').textContent = `${names.length} online`;
      document.getElementById('last-update').textContent = new Date().toLocaleTimeString();
      document.getElementById('difficulty-select').value = data.difficulty || 'medium';
      document.getElementById('chat-level-select').value = data.chat_level || 'high';
      document.getElementById('summary').innerHTML = renderSummary(names, bots);
      document.getElementById('status-bar').innerHTML = renderStatus(names, bots);
      document.getElementById('companion-grid').innerHTML = names.length
        ? names.map(n => renderCard(n, bots[n])).join('')
        : '<div class="empty">No companions online yet.</div>';
      document.getElementById('threat-watch').innerHTML = renderThreatWatch(names, bots);
      document.getElementById('player-memory').innerHTML = renderPlayerMemory(data.players || {});
    })
    .catch(() => {
      document.getElementById('status-bar').innerHTML = '<span class="status-pill bad">Connection error - is the brain server running?</span>';
    });
}

function applyDifficulty() {
  const val = document.getElementById('difficulty-select').value;
  fetch('/api/control/difficulty', {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify({difficulty: val})
  }).then(refresh);
}

function applyChatLevel() {
  const val = document.getElementById('chat-level-select').value;
  fetch('/api/control/chat-level', {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify({chat_level: val})
  }).then(refresh);
}

function setControlMessage(message, tone = '') {
  const el = document.getElementById('control-message');
  el.textContent = message;
  el.className = `badge control-message ${tone}`;
}

function postControl(path, payload) {
  setControlMessage('Working...', '');
  return fetch(path, {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify(payload || {})
  }).then(async response => {
    const data = await response.json().catch(() => ({}));
    if (!response.ok || data.ok === false) {
      throw new Error(data.error || 'Control request failed');
    }
    return data;
  });
}

function addBot() {
  const name = document.getElementById('bot-name-input').value.trim();
  const role = document.getElementById('bot-role-select').value;
  postControl('/api/control/bots/add', {name, role})
    .then(data => {
      document.getElementById('bot-name-input').value = '';
      setControlMessage(`Added ${data.name || 'bot'}`, 'good');
      refresh();
    })
    .catch(error => setControlMessage(error.message, 'bad'));
}

function removeBot(name) {
  if (!confirm(`Remove ${name}?`)) return;
  postControl('/api/control/bots/remove', {name})
    .then(() => {
      setControlMessage(`Removed ${name}`, 'good');
      refresh();
    })
    .catch(error => setControlMessage(error.message, 'bad'));
}

function clearBots() {
  if (!confirm('Remove every AI companion?')) return;
  postControl('/api/control/bots/clear', {})
    .then(data => {
      setControlMessage(`Removed ${data.removed || 0}`, 'good');
      refresh();
    })
    .catch(error => setControlMessage(error.message, 'bad'));
}

document.addEventListener('mouseup', () => { schematicPainting = false; });
setupSchematicLayers();
renderSchematicGrid();
loadSchematicList();
refresh();
setInterval(refresh, 3000);
</script>
</body>
</html>"""


@app.route("/dashboard")
def dashboard():
    return DASHBOARD_HTML


@app.route("/api/schematics", methods=["GET", "POST"])
def schematics_collection():
    schematics = load_custom_schematics()
    if request.method == "GET":
        return jsonify({
            "schematics": [
                {"name": name, "block_count": len(data.get("blocks", []))}
                for name, data in sorted(schematics.items())
            ]
        })

    payload = request.json or {}
    name = normalize_schematic_name(payload.get("name", ""))
    raw_blocks = payload.get("blocks", [])
    if not name:
        return jsonify({"error": "Give the schematic a name."}), 400
    if not isinstance(raw_blocks, list) or not raw_blocks:
        return jsonify({"error": "Paint at least one block before saving."}), 400
    if len(raw_blocks) > 1500:
        return jsonify({"error": "This schematic is too large (maximum 1500 blocks)."}), 400

    blocks = []
    seen = set()
    for entry in raw_blocks:
        try:
            x, y, z = int(entry["x"]), int(entry["y"]), int(entry["z"])
            block = str(entry["block"]).lower()
            raw_properties = entry.get("properties", {})
        except (KeyError, TypeError, ValueError):
            return jsonify({"error": "One of the painted blocks is invalid."}), 400
        if not (-32 <= x <= 32 and -8 <= y <= 32 and -32 <= z <= 32):
            return jsonify({"error": "A block is outside the supported blueprint bounds."}), 400
        if not re.fullmatch(r"[a-z0-9_.-]+:[a-z0-9_./-]+", block) or block == "minecraft:air":
            return jsonify({"error": f"Unsupported block id: {block}"}), 400
        if not isinstance(raw_properties, dict):
            return jsonify({"error": "Block properties must be an object."}), 400
        properties = {}
        for prop_name, prop_value in raw_properties.items():
            prop_name = str(prop_name).lower()
            prop_value = str(prop_value).lower()
            if not re.fullmatch(r"[a-z0-9_]+", prop_name) or not re.fullmatch(r"[a-z0-9_]+", prop_value):
                return jsonify({"error": f"Invalid block property: {prop_name}={prop_value}"}), 400
            properties[prop_name] = prop_value
        key = (x, y, z)
        if key in seen:
            continue
        seen.add(key)
        blocks.append({"x": x, "y": y, "z": z, "block": block, "properties": properties})

    schematics[name] = {
        "name": name,
        "blocks": blocks,
        "radius": max(max(abs(b["x"]), abs(b["z"])) for b in blocks),
        "height": max(b["y"] for b in blocks) + 1,
        "updated_at": datetime.now().isoformat(timespec="seconds"),
    }
    save_custom_schematics(schematics)
    return jsonify({"ok": True, "name": name, "block_count": len(blocks)})


@app.route("/api/schematics/<name>", methods=["GET", "DELETE"])
def schematic_item(name):
    normalized = normalize_schematic_name(name)
    schematics = load_custom_schematics()
    if normalized not in schematics:
        return jsonify({"error": "Schematic not found."}), 404
    if request.method == "DELETE":
        del schematics[normalized]
        save_custom_schematics(schematics)
        return jsonify({"ok": True})
    return jsonify(schematics[normalized])


@app.route("/api/dashboard")
def api_dashboard():
    all_bots = load_all_bots_from_registry()
    return jsonify({
        "bots": all_bots,
        "players": load_player_memory(),
        "difficulty": DIFFICULTY,
        "chat_level": CHAT_LEVEL,
    })


@app.route("/api/control/difficulty", methods=["POST"])
def control_difficulty():
    global DIFFICULTY
    val = request.json.get("difficulty", "medium")
    if val in DIFFICULTY_SETTINGS:
        DIFFICULTY = val
        print(f"[CONTROL] Difficulty changed to: {val}")
    return jsonify({"difficulty": DIFFICULTY})


@app.route("/api/control/chat-level", methods=["POST"])
def control_chat_level():
    global CHAT_LEVEL
    val = str((request.json or {}).get("chat_level", "high")).lower()
    if val in {"low", "medium", "high"}:
        CHAT_LEVEL = val
        settings = load_json_file(SERVER_SETTINGS_FILE, {})
        settings["chat_level"] = CHAT_LEVEL
        save_json_file(SERVER_SETTINGS_FILE, settings)
        print(f"[CONTROL] Chit chat level changed to: {CHAT_LEVEL}")
    return jsonify({"chat_level": CHAT_LEVEL})


@app.route("/api/control/bots/add", methods=["POST"])
def control_add_bot():
    payload = request.json or {}
    result = call_minecraft_control("/api/companion/spawn", {
        "name": payload.get("name", ""),
        "role": payload.get("role", ""),
        "player": payload.get("player", ""),
    })
    if not result.get("ok"):
        return jsonify(result), 502

    name = result.get("name", "Bot")
    bot = active_bots.setdefault(name, create_default_bot_state())
    bot["appearance_variant"] = result.get("role", bot.get("appearance_variant", "wanderer"))
    bot["x"] = result.get("x", bot.get("x", 0))
    bot["y"] = result.get("y", bot.get("y", 0))
    bot["z"] = result.get("z", bot.get("z", 0))
    set_bot_action(bot, "@idle")
    bot["task"] = "standing by"
    save_bot_state()
    print(f"[CONTROL] Added bot from dashboard: {name}")
    return jsonify(result)


@app.route("/api/control/bots/remove", methods=["POST"])
def control_remove_bot():
    payload = request.json or {}
    name = payload.get("name", "").strip()
    result = call_minecraft_control("/api/companion/remove", {"name": name})
    if not result.get("ok"):
        return jsonify(result), 502

    if name:
        active_bots.pop(name, None)
        save_bot_state()
    print(f"[CONTROL] Removed bot from dashboard: {name}")
    return jsonify(result)


@app.route("/api/control/bots/clear", methods=["POST"])
def control_clear_bots():
    result = call_minecraft_control("/api/companion/clear", {})
    if not result.get("ok"):
        return jsonify(result), 502

    active_bots.clear()
    save_bot_state()
    print("[CONTROL] Cleared bots from dashboard")
    return jsonify(result)


if __name__ == "__main__":
    sync_active_bots_from_registry(remove_stale=True)
    print("=================================================")
    print("  AI Companion Brain — Port 8080                ")
    print(f"  Mood: ON | Memory: ON | Proactive: {DIFFICULTY_SETTINGS[DIFFICULTY]['proactive']}")
    print(f"  Difficulty: {DIFFICULTY}                     ")
    print(f"  LLM model: {LLM_MODEL}")
    print(f"  LLM base URL: {LLM_BASE_URL or 'OpenAI default'}")
    print(f"  Server folder: {os.path.dirname(BASE_DIR)}")
    print(f"  Companion registry: {COMPANION_REGISTRY_FILE}")
    print("=================================================")
    app.run(host="127.0.0.1", port=8080)
