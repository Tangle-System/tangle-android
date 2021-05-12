package com.tangle.tanglelibrary;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TangleParse {

    final String TAG = this.getClass().getName();

    /* no code or command used by decoder as a validation */

    final static int MODIFIER_SWITCH_NONE = 0;
    final static int MODIFIER_SWITCH_RG = 1;
    final static int MODIFIER_SWITCH_GB = 2;
    final static int MODIFIER_SWITCH_BR = 3;

    final static int DEVICE_ID_APP = 255;

    final static int NONE = 0;

    /* filters 1 -> 30 */
    final static int FILTER_NONE = 1;
    final static int FILTER_BLUR = 2;
    final static int FILTER_COLOR_SHIFT = 3;
    final static int FILTER_MIRROR = 4;
    final static int FILTER_SCATTER = 5;

    /* drawings 31 -> 36 */
    final static int DRAWING_SET = 31;
    final static int DRAWING_ADD = 32;
    final static int DRAWING_SUB = 33;
    final static int DRAWING_SCALE = 34;
    final static int DRAWING_FILTER = 35;

    /* windows 37 -> 42 */
    final static int WINDOW_SET = 37;
    final static int WINDOW_ADD = 38;
    final static int WINDOW_SUB = 39;
    final static int WINDOW_SCALE = 40;
    final static int WINDOW_FILTER = 41;

    /* frame 42 */
    final static int FRAME = 42;

    /* clip 43 */
    final static int CLIP = 43;

    /* sifters 46 -> 52 */
    final static int SIFT_DEVICE = 46;
    final static int SIFT_TANGLE = 47;
    final static int SIFT_GROUP = 48;

    /* event handler 53*/
    final static int HANDLER = 53;

    /* animations 54 -> 182 */
    final static int ANIMATION_NONE = 54;
    final static int ANIMATION_FILL = 55;
    final static int ANIMATION_RAINBOW = 56;
    final static int ANIMATION_FADE = 57;
    final static int ANIMATION_PROJECTILE = 58;
    final static int ANIMATION_LOADING = 59;
    final static int ANIMATION_COLOR_ROLL = 60;
    final static int ANIMATION_PALLETTE_ROLL = 61;
    final static int ANIMATION_INL_ANI = 62;
    final static int ANIMATION_DEFINED = 63;

    /* modifiers and filters 189 -> 206 */
    final static int MODIFIER_BRIGHTNESS = 189;
    final static int MODIFIER_TIMELINE = 190;
    final static int MODIFIER_FADE_IN = 191;
    final static int MODIFIER_FADE_OUT = 192;
    final static int MODIFIER_SWITCH_COLORS = 193;
    final static int MODIFIER_TIME_LOOP = 194;
    final static int MODIFIER_TIME_SCALE = 195;
    final static int MODIFIER_TIME_CHANGE = 196;

    /* variables 207 -> 222 */
    final static int DEVICE = 207;
    final static int TANGLE = 208;
    final static int PIXELS = 209;
    final static int PORT = 210;
    final static int GROUP = 211;
    final static int MARK = 212;
    final static int CONSTANT = 213;
    final static int CHANNEL = 214;
    final static int EVENT = 215;

    /* definitions 223 -> 230 */
    final static int DEFINE_DEVICE_1PORT = 223;
    final static int DEFINE_DEVICE_2PORT = 224;
    final static int DEFINE_DEVICE_4PORT = 225;
    final static int DEFINE_DEVICE_8PORT = 226;
    final static int DEFINE_TANGLE = 227;
    final static int DEFINE_GROUP = 228;
    final static int DEFINE_MARKS = 229;
    final static int DEFINE_ANIMATION = 230;

    /* events 231 -> 240 */
    final static int EVENT_EMIT = 231;
    final static int EVENT_ON = 232;
    final static int EVENT_SET_PARAM = 233;

    /* channels 240 -> 250 */
    final static int CHANNEL_WRITE = 240;
    final static int CHANNEL_PARAMETER_VALUE = 241;
    final static int CHANNEL_PARAMETER_VALUE_SMOOTHED = 242;
    final static int CHANNEL_ADD_VALUES = 243;
    final static int CHANNEL_SUB_VALUES = 244;
    final static int CHANNEL_MUL_VALUES = 245;
    final static int CHANNEL_DIV_VALUES = 246;
    final static int CHANNEL_MOD_VALUES = 247;
    final static int CHANNEL_SCALE_VALUE = 248;
    final static int CHANNEL_MAP_VALUE = 249;

    /* command flags */
    final static int FLAG_TNGL_BYTES = 251;
    final static int FLAG_SET_TIMELINE = 252;
    final static int FLAG_EMIT_EVENT = 253;

    /* command ends */
    final static int END_OF_STATEMENT = 254;
    final static int END_OF_TNGL_BYTES = 255;

    ByteArrayOutputStream payload = new ByteArrayOutputStream();

    public byte[] getPayload(String code) {
        parseCode(code);

        return payload.toByteArray();
    }

    private void fillCommand(int code) {
        payload.write(code);
    }

    private void fillByte(byte value) {
        payload.write(value);
    }

    private void fillBytes(byte[] value) {
        for (byte b : value) {
            payload.write(b);
        }
    }

    private void fillUInt8(int value) {
        payload.write(value);
    }

    private void fillInt16(int value) {
        byte[] result = new byte[2];
        for (int i = 0; i < 2; i++) {
            result[i] = (byte) (value & 0xFF);
            value >>= Byte.SIZE;
        }
        try {
            payload.write(result);
        } catch (Exception e) {
            Log.e(TAG, "" + e);
        }
    }

    private void fillInt32(int value) {
        byte[] result = new byte[4];
        for (int i = 0; i < 4; i++) {
            result[i] = (byte) (value & 0xFF);
            value >>= Byte.SIZE;
        }
        try {
            payload.write(result);
        } catch (Exception e) {
            Log.e(TAG, "" + e);
        }
    }

    private void fillRGB(String color) {
        if (color.length() == 7) {
            color = color.substring(1);
            fillUInt8(Integer.decode("#" + color.substring(0, 2)));
            fillUInt8(Integer.decode("#" + color.substring(2, 4)));
            fillUInt8(Integer.decode("#" + color.substring(4, 6)));
        } else {
            Log.e(TAG, "fillRGB: Have wrong color stamp");
        }
    }

    private void fillString(String s) {
        byte[] result = new byte[8];
        if (s.length() >= 8) {
            for (int i = 0; i < 8; i++) {
                result[i] = (byte) s.charAt(i);
            }
        } else {
            for (int i = 0; i < s.length(); i++) {
                result[i] = (byte) s.charAt(i);
            }
        }
        try {
            payload.write(result);
        } catch (IOException e) {
            Log.e(TAG, "fillString: " + e);
        }
    }

    private void fillPercentage(double percent) {
        payload.write((int) Math.floor((percent / 100) * 255));
    }

    private Map<String, Pattern> setPatterns() {
        Map<String, Pattern> patterns = new LinkedHashMap<String, Pattern>();
        patterns.put("comment", Pattern.compile("\\/\\/[^\\n]*"));
        patterns.put("htmlrgb", Pattern.compile("#([0-9a-f][0-9a-f])([0-9a-f][0-9a-f])([0-9a-f][0-9a-f])", Pattern.MULTILINE));
        patterns.put("string", Pattern.compile("\"([\\w ]*)\"", Pattern.MULTILINE));
        patterns.put("arrow", Pattern.compile("->"));
        patterns.put("char", Pattern.compile("'([\\W,\\w])'", Pattern.MULTILINE));
        patterns.put("byte", Pattern.compile("(0[xX][0-9a-fA-F][0-9a-fA-F](?![0-9a-fA-F]))", Pattern.MULTILINE));
        patterns.put("word", Pattern.compile("([a-zA-Z_][a-zA-Z_0-9]*)", Pattern.MULTILINE));
        patterns.put("percentage", Pattern.compile("([\\d.]+)%", Pattern.MULTILINE));
        patterns.put("float", Pattern.compile("([+-]?[0-9]*[.][0-9]+)", Pattern.MULTILINE));
        patterns.put("number", Pattern.compile("([+-]?[0-9]+)", Pattern.MULTILINE));
        patterns.put("whitespace", Pattern.compile("(\\s+)", Pattern.MULTILINE));
        patterns.put("punctuation", Pattern.compile("([^\\w\\s])", Pattern.MULTILINE));
        return patterns;
    }

    public ArrayList<ArrayList> getToken(String code, Map<String, Pattern> patterns) {
        int codeLength;
        Matcher matcher;
        ArrayList<String> token = new ArrayList<>();
        ArrayList<ArrayList> tokens = new ArrayList<>();

        while (!code.isEmpty()) {
            codeLength = code.length();
            for (String key : patterns.keySet()) {
                matcher = patterns.get(key).matcher(code);
                if (matcher.find() && matcher.start() < codeLength) {
                    token.clear();
                    token.add(key);
                    token.add(matcher.group(0));
                    codeLength = matcher.start();
                }
            }
            if (codeLength > 0) {
                ArrayList<String> push = new ArrayList<>();
                push.add(code.substring(0, codeLength));
                push.add("unknown");
                tokens.add(push);
            }
            if (!token.isEmpty()) {
                tokens.add(token);
            }
            code = code.substring(codeLength + (token.get(1).length()));
            token = new ArrayList<>();
        }

        return tokens;
    }

    public void parseCode(String code) {
        setPatterns();
        ArrayList<ArrayList> tokens = getToken(code, setPatterns());
        payload.reset();

        fillCommand(FLAG_TNGL_BYTES);

        for (int i = 0; i < tokens.size(); i++) {
            ArrayList<String> token = tokens.get(i);
            switch (token.get(0)) {
                case "whitespace":
                    continue;
                case "char":
                    fillUInt8(token.get(1).charAt(0));
                    break;
                case "byte":
                    fillUInt8(Integer.decode(token.get(1)));
                    break;
                case "string":
                    String s = token.get(1);
                    s = s.substring(1);
                    s = s.substring(0, s.length() - 1);
                    fillString(s);
                    break;
                case "punctuation":
                    if (token.get(1).equals("}")) {
                        fillCommand(END_OF_STATEMENT);
                    }
                    break;
                case "word":
                    switch (token.get(1)) {
                        // === true, false ===
                        case "true":
                            fillUInt8(1);
                            break;
                        case "false":
                            fillUInt8(0);
                            break;
                        // === canvas operations ===
                        case "setDrawing":
                            fillCommand(DRAWING_SET);
                            break;
                        case "addDrawing":
                            fillCommand(DRAWING_ADD);
                            break;
                        case "subDrawing":
                            fillCommand(DRAWING_SUB);
                            break;
                        case "scaDrawing":
                            fillCommand(DRAWING_SCALE);
                            break;
                        case "filDrawing":
                            fillCommand(DRAWING_FILTER);
                            break;
                        case "setWindow":
                            fillCommand(WINDOW_SET);
                            break;
                        case "addWindow":
                            fillCommand(WINDOW_ADD);
                            break;
                        case "subWindow":
                            fillCommand(WINDOW_SUB);
                            break;
                        case "scaWindow":
                            fillCommand(WINDOW_SCALE);
                            break;
                        case "filWindow":
                            fillCommand(WINDOW_FILTER);
                            break;
                        // === time operations ===
                        case "frame":
                            fillCommand(FRAME);
                            break;
                        // === animations ===
                        case "animNone":
                            fillCommand(ANIMATION_NONE);
                            break;
                        case "animationDefined":
                            fillCommand(ANIMATION_DEFINED);
                            break;
                        case "animFill":
                            fillCommand(ANIMATION_FILL);
                            break;
                        case "animRainbow":
                            fillCommand(ANIMATION_RAINBOW);
                            break;
                        case "animPlasmaShot":
                            fillCommand(ANIMATION_PROJECTILE);
                            break;
                        case "animLoadingBar":
                            fillCommand(ANIMATION_LOADING);
                            break;
                        case "animFade":
                            fillCommand(ANIMATION_FADE);
                            break;
                        case "animColorRoll":
                            fillCommand(ANIMATION_COLOR_ROLL);
                            break;
                        case "animPaletteRoll":
                            fillCommand(ANIMATION_PALLETTE_ROLL);
                            break;
                        // === handlers ===
                        case "eventHandler":
                            fillCommand(HANDLER);
                            break;
                        // === clip ===
                        case "clip":
                            fillCommand(CLIP);
                            break;
                        // === definitions ===
                        case "defAnimation":
                            fillCommand(DEFINE_ANIMATION);
                            break;
                        case "defDevice1":
                            fillCommand(DEFINE_DEVICE_1PORT);
                            break;
                        case "defDevice2":
                            fillCommand(DEFINE_DEVICE_2PORT);
                            break;
                        case "defDevice4":
                            fillCommand(DEFINE_DEVICE_4PORT);
                            break;
                        case "defDevice8":
                            fillCommand(DEFINE_DEVICE_8PORT);
                            break;
                        case "defTangle":
                            fillCommand(DEFINE_TANGLE);
                            break;
                        case "defGroup":
                            fillCommand(DEFINE_GROUP);
                            break;
                        case "defMarks":
                            fillCommand(DEFINE_MARKS);
                            break;
                        // === sifters ===
                        case "sifDevices":
                            fillCommand(SIFT_DEVICE);
                            break;
                        case "siftTangles":
                            fillCommand(SIFT_TANGLE);
                            break;
                        case "siftGroups":
                            fillCommand(SIFT_GROUP);
                            break;
                        // === variables ===
                        case "device":
                            fillCommand(DEVICE);
                            break;
                        case "tangle":
                            fillCommand(TANGLE);
                            break;
                        case "pixels":
                            fillCommand(PIXELS);
                            break;
                        case "port":
                            fillCommand(PORT);
                            break;
                        case "group":
                            fillCommand(GROUP);
                            break;
                        case "mark":
                            fillCommand(MARK);
                            break;
                        case "constant":
                            fillCommand(CONSTANT);
                            break;
                        case "channel":
                            fillCommand(CHANNEL);
                            break;
                        case "event":
                            fillCommand(EVENT);
                            break;
                        // === modifiers ===
                        case "modifyBrightness":
                            fillCommand(MODIFIER_BRIGHTNESS);
                            break;
                        case "modifyTimeline":
                            fillCommand(MODIFIER_TIMELINE);
                            break;
                        case "modifyFadeIn":
                            fillCommand(MODIFIER_FADE_IN);
                            break;
                        case "modifyFadeOut":
                            fillCommand(MODIFIER_FADE_OUT);
                            break;
                        case "modifyColorSwitch":
                            fillCommand(MODIFIER_SWITCH_COLORS);
                            break;
                        case "modifyTimeLoop":
                            fillCommand(MODIFIER_TIME_LOOP);
                            break;
                        case "modifyTimeScale":
                            fillCommand(MODIFIER_TIME_SCALE);
                            break;
                        case "modifyTimeChange":
                            fillCommand(MODIFIER_TIME_CHANGE);
                            break;
                        // === filters ===
                        case "filterNone":
                            fillCommand(FILTER_NONE);
                            break;
                        case "filterBlur":
                            fillCommand(FILTER_BLUR);
                            break;
                        case "filterColorShift":
                            fillCommand(FILTER_COLOR_SHIFT);
                            break;
                        case "filterMirror":
                            fillCommand(FILTER_MIRROR);
                            break;
                        case "filterScatter":
                            fillCommand(FILTER_SCATTER);
                            break;
                        // === channels ===
                        case "writeChannel":
                            fillCommand(CHANNEL_WRITE);
                            break;
                        case "eventParameterValue":
                            fillCommand(CHANNEL_PARAMETER_VALUE);
                            break;
                        case "eventParameterValueSmoothed":
                            fillCommand(CHANNEL_PARAMETER_VALUE_SMOOTHED);
                            break;
                        case "addValues":
                            fillCommand(CHANNEL_ADD_VALUES);
                            break;
                        case "subValues":
                            fillCommand(CHANNEL_SUB_VALUES);
                            break;
                        case "mulValues":
                            fillCommand(CHANNEL_MUL_VALUES);
                            break;
                        case "divValues":
                            fillCommand(CHANNEL_DIV_VALUES);
                            break;
                        case "modValues":
                            fillCommand(CHANNEL_MOD_VALUES);
                            break;
                        case "scaValue":
                            fillCommand(CHANNEL_SCALE_VALUE);
                            break;
                        case "mapValue":
                            fillCommand(CHANNEL_MAP_VALUE);
                            break;
                        // === events ===
                        case "emitEvent":
                            fillCommand(EVENT_EMIT);
                            break;
                        case "onEvent":
                            fillCommand(EVENT_ON);
                            break;
                        case "setEventParam":
                            fillCommand(EVENT_SET_PARAM);
                            break;
                        // === constants ===
                        case"MODIFIER_SWITCH_NONE":
                            fillCommand(MODIFIER_SWITCH_NONE);
                            break;
                        case "MODIFIER_SWITCH_RG":
                            fillCommand(MODIFIER_SWITCH_RG);
                            break;
                        case "MODIFIER_SWITCH_GR":
                            fillCommand(MODIFIER_SWITCH_RG);
                            break;
                        case "MODIFIER_SWITCH_GB":
                            fillCommand(MODIFIER_SWITCH_GB);
                            break;
                        case "MODIFIER_SWITCH_BG":
                            fillCommand(MODIFIER_SWITCH_GB);
                            break;
                        case "MODIFIER_SWITCH_BR":
                            fillCommand(MODIFIER_SWITCH_BR);
                            break;
                        case "MODIFIER_SWITCH_RB":
                            fillCommand(MODIFIER_SWITCH_BR);
                    }
                    break;
                case "percentage":
                    s = token.get(1);
                    s = s.substring(0, s.length() - 1);
                    fillPercentage(Double.parseDouble(s));
                    break;
                case "number":
                    fillInt32(Integer.parseInt(token.get(1)));
                    break;
                case "htmlrgb":
                    fillRGB(token.get(1));
                    break;
                case "comment":
                    // NOP
                case "arrow":
                    // NOP
            }
        }
        fillCommand(END_OF_TNGL_BYTES);
    }
}
