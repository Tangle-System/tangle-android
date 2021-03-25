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

    final static int NONE = 0;
    final static int TOUCH = 1;
    final static int MOVEMENT = 2;
    final static int KEYPRESS = 3;
    final static int TEST = 255;

 ////////////////////////////////////////////////////////////////////

    /* handlers 1 -> 30 */
    final static int HANDLER_TOUCH = 1;
    final static int HANDLER_MOVEMENT = 2;
    final static int HANDLER_KEYPRESS = 3;

    /* drawings 31 -> 36 */
    final static int DRAWING_SET = 31;
    final static int DRAWING_ADD = 32;
    final static int DRAWING_SUB = 33;
    final static int DRAWING_MUL = 34;
    final static int DRAWING_FIL = 35;

    /* windows 37 -> 42 */
    final static int WINDOW_SET = 37;
    final static int WINDOW_ADD = 38;
    final static int WINDOW_SUB = 39;
    final static int WINDOW_MUL = 40;
    final static int WINDOW_FIL = 41;

    /* frame 43 */
    final static int FRAME = 43;

    /* clip 44 */
    final static int CLIP = 44;

    /* time manipulation 45 */
    final static int TIMETRANSFORMER = 45;

    /* sifters 46 -> 53 */
    final static int SIFT_DEVICE = 46;
    final static int SIFT_TANGLE = 47;
    final static int SIFT_GROUP = 48;

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

    /* effects 189 -> 206 */
    final static int EFFECT_FADEIN = 189;
    final static int EFFECT_FADEOUT = 190;
    final static int EFFECT_BLURE = 191;
    final static int EFFECT_SCATTER = 192;
    final static int EFFECT_STRIPEIFY = 193;
    final static int EFFECT_INVERT = 194;

    /* variables 207 -> 222 */
    final static int DEVICE = 207;
    final static int TANGLE = 208;
    final static int PIXELS = 209;
    final static int NEOPIXEL = 210;
    final static int GROUP = 211;
    final static int MARK = 212;

    /* definitions 223 -> 238 */
    final static int DEFINE_DEVICE = 223;
    final static int DEFINE_TANGLE = 224;
    final static int DEFINE_GROUP = 225;
    final static int DEFINE_MARKS = 226;

    /* control codes 239 -> 254 */
    final static int COMMAND_SET_TIME_OFFSET = 239;

    final static int FLAG_TNGL_BYTES = 240;
    final static int FLAG_TRIGGER = 241;
    final static int FLAG_SYNC_TIMELINE = 242;

    /* end of statements with no boundary 255 */
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

    private void fillUInt8(int value) {
        payload.write(value);
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

    private void fillByte(byte value) {
        payload.write(value);
    }

    private void fillBytes(byte[] value) {
        for (byte b : value) {
            payload.write(b);
        }
    }

    private void fillRGB(String color) {
        if (color.length() == 7){
            color = color.substring(1);
            fillUInt8(Integer.decode("#" + color.substring(0,2)));
            fillUInt8(Integer.decode("#" + color.substring(2,4)));
            fillUInt8(Integer.decode("#" + color.substring(4,6)));
        } else {
            Log.e(TAG, "fillRGB: Have wrong color stamp");
        }
    }

    private void fillString(String s){
        byte[] result = new byte[8];
        if(s.length() >= 8){
            for (int i = 0; i < 8;i++){
                result[i] = (byte) s.charAt(i);
            }
        } else {
            for (int i = 0; i < s.length();i++){
                result[i] = (byte) s.charAt(i);
            }
        }
        try {
            payload.write(result);
        } catch (IOException e){
            Log.e(TAG, "fillString: " + e );
        }
    }

    private void fillPercentage(double percent) {
        payload.write((int) Math.floor((percent / 100) * 255));
    }

    private Map<String, Pattern> setPatterns() {
        Map<String, Pattern> patterns = new LinkedHashMap<String, Pattern>();
        patterns.put("htmlrgb", Pattern.compile("#([0-9a-f][0-9a-f])([0-9a-f][0-9a-f])([0-9a-f][0-9a-f])", Pattern.MULTILINE));
        patterns.put("string", Pattern.compile("\"([\\w ]*)\"", Pattern.MULTILINE));
        patterns.put("char", Pattern.compile("'([\\W,\\w])'", Pattern.MULTILINE));
        patterns.put("byte", Pattern.compile("(0[xX][0-9a-fA-F][0-9a-fA-F](?![0-9a-fA-F]))", Pattern.MULTILINE));
        patterns.put("word", Pattern.compile("([a-zA-Z_]+)", Pattern.MULTILINE));
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
                    if (token.get(1) == "punctuation") {
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
                        case "mulDrawing":
                            fillCommand(DRAWING_MUL);
                            break;
                        case "filDrawing":
                            fillCommand(DRAWING_FIL);
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
                        case "mulWindow":
                            fillCommand(WINDOW_MUL);
                            break;
                        case "filWindow":
                            fillCommand(WINDOW_FIL);
                            break;
                        // === time operations ===
                        case "frame":
                            fillCommand(FRAME);
                            break;
                        case "timetransformer":
                            fillCommand(TIMETRANSFORMER);
                            break;
                        // === animations ===
                        case "animNone":
                            fillCommand(ANIMATION_NONE);
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
                        case "handlerTouch":
                            fillCommand(HANDLER_TOUCH);
                            break;
                        case "handlerMovement":
                            fillCommand(HANDLER_MOVEMENT);
                            break;
                        case "handlerKeyPress":
                            fillCommand(HANDLER_KEYPRESS);
                            break;
                        // === clip ===
                        case "clip":
                            fillCommand(CLIP);
                            break;
                        // === definitions ===
                        case "defDevice":
                            fillCommand(DEFINE_DEVICE);
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
                        case "neopixel":
                            fillCommand(NEOPIXEL);
                            break;
                        case "group":
                            fillCommand(GROUP);
                            break;
                        case "mark":
                            fillCommand(MARK);
                            break;
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
            }
        }
        fillCommand(END_OF_TNGL_BYTES);
    }

}
