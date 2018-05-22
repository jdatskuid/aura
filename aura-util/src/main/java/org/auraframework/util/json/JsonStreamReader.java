/*
 * Copyright (C) 2013 salesforce.com, inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.auraframework.util.json;

import static org.auraframework.util.json.JsonConstant.ARRAY;
import static org.auraframework.util.json.JsonConstant.ARRAY_END;
import static org.auraframework.util.json.JsonConstant.ARRAY_START;
import static org.auraframework.util.json.JsonConstant.BINARY_STREAM;
import static org.auraframework.util.json.JsonConstant.BOOLEAN;
import static org.auraframework.util.json.JsonConstant.COMMENT_DELIM;
import static org.auraframework.util.json.JsonConstant.ENTRY_SEPARATOR;
import static org.auraframework.util.json.JsonConstant.FUNCTION;
import static org.auraframework.util.json.JsonConstant.FUNCTION_ARGS_END;
import static org.auraframework.util.json.JsonConstant.FUNCTION_ARGS_START;
import static org.auraframework.util.json.JsonConstant.FUNCTION_BODY;
import static org.auraframework.util.json.JsonConstant.LITERAL;
import static org.auraframework.util.json.JsonConstant.LITERAL_START;
import static org.auraframework.util.json.JsonConstant.MULTICOMMENT_DELIM;
import static org.auraframework.util.json.JsonConstant.NULL;
import static org.auraframework.util.json.JsonConstant.NUMBER;
import static org.auraframework.util.json.JsonConstant.OBJECT;
import static org.auraframework.util.json.JsonConstant.OBJECT_END;
import static org.auraframework.util.json.JsonConstant.OBJECT_SEPARATOR;
import static org.auraframework.util.json.JsonConstant.OBJECT_START;
import static org.auraframework.util.json.JsonConstant.STRING;
import static org.auraframework.util.json.JsonConstant.WHITESPACE;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackReader;
import java.io.Reader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;

import org.auraframework.util.LimitedLengthInputStream;
import org.auraframework.util.LimitedLengthInputStream.StreamFinishedListener;
import org.auraframework.util.Utf8InputStreamReader;
import org.auraframework.util.json.JsonHandler.JsonValidationException;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import javax.annotation.Nonnull;

/**
 * Reads a stream of json-formatted objects. Call next() to parse the next thing
 * in the stream, Then call getValue() to get it.<br>
 * <br>
 * This parser handles everything that is specified in the json spec on
 * http://www.json.org/ <br>
 * AND<br>
 * <br>
 * It handles a few extra things that are not supported in the spec. These are:<br>
 * <br>
 * -Strings can be quoted with ' or "  (spec says only ")<br>
 * <br>
 * -Keys in maps/objects can be unquoted (spec says they must be quoted)<br>
 * e.g. foo : 'bar' or 'foo' : 'bar' or "foo" : "bar" are all acceptable.<br>
 * <br>
 * -Supports a few escape sequences that the spec doesn't mention : \', \0, \v<br>
 * <br>
 * -Javascript functions can be values in maps or arrays. These are only
 * supported when they are in the format of function(arg1,arg2){}. Named
 * functions are not supported, as in json the names are supplied as the key in
 * a map instead. functions will be returned as lib.json.JsonFunction objects.<br>
 * <br>
 * -Binary streams in Salesforce.com's JSON+binary format are supported, which
 * uses the backtick character as a delimiter and the format:<br>
 * `[length of data in bytes as a 64-bit big-endian binary number][raw binary
 * data]`<br>
 * This is enabled only when a JsonStreamReader is constructed using an
 * InputStream.<br>
 * <br>
 * ===============<br>
 * <br>
 * default json->java mappings:<br>
 * json object(map) ==> java.util.HashMap<String, Object><br>
 * json String ==> java.lang.String<br>
 * json Number ==> java.math.BigDecimal<br>
 * json boolean ==> boolean<br>
 * json null ==> null<br>
 * json array ==> java.util.ArrayList&lt;Object&gt;<br>
 * js function ==> lib.json.JsFunction<br>
 * json binary ==> java.io.InputStream<br>
 * <br>
 * If you want to provide your own mappings (rather than those above), you can
 * pass in your own implementation of JsonHandlerProvider, and then your
 * JsonHandlers will be used. This allows you to skip the intermediate step of
 * parsing into Maps and Lists, and lets you put the primitives directly into
 * your objects as they are parsed.
 */
public class JsonStreamReader {

    // When we JSON.parse, it needs to load all the bytes into memory. 
    // Allowing any amount of bytes would allow someone to compromise our servers.
    // 4mb seems to be industry standard here.
    private static final int MAX_LENGTH = 4194304;
    private static JsonHandlerProvider defaultProvider = new JsonHandlerProviderImpl();
    private static Map<Character, Character> escapes = Maps.newHashMapWithExpectedSize(13);
    static {
        escapes.put('"', '"');
        escapes.put('\\', '\\');
        escapes.put('/', '/');
        escapes.put('b', '\b');
        escapes.put('f', '\f');
        escapes.put('n', '\n');
        escapes.put('r', '\r');
        escapes.put('t', '\t');
        escapes.put('\'', '\'');
        escapes.put('0', '\0');
        escapes.put('v', '\u000B');
    }

    private final PushbackReader reader;

    private JsonConstant currentToken;
    private Object current;
    private int charNum = 0;
    private int colNum = 1; // current parse column number
    private int prevColNum = 0; // max colNum of previously parsed line
    private int lineNum = 1; // current parse line number (start at 1)
    private int lastLineNum = 0;        // the line number before the last token.
    private int lastColNum = 0;         // the column number before the last token.
    private JsonHandlerProvider provider;
    private final DataInputStream binaryInput;
    private boolean recursiveRead = true;
    private boolean lengthLimitsEnabled = true;

    public JsonStreamReader(Reader reader, JsonHandlerProvider provider) {
        this(reader, null, provider);
    }

    private JsonStreamReader(Reader reader, InputStream binaryInput, JsonHandlerProvider provider) {
        if (reader == null) {
            throw new JsonParseException("Reader cannot be null");
        }
        this.reader = new PushbackReader(reader, 2);
        this.binaryInput = binaryInput == null ? null
                : (binaryInput instanceof DataInputStream ? (DataInputStream) binaryInput : new DataInputStream(
                        binaryInput));
        this.provider = provider;
    }

    public JsonStreamReader(String string, JsonHandlerProvider provider) {
        this(createStringReader(string), null, provider);
    }

    /**
     * Creates a JSON stream reader that also understands our proprietary JSON
     * binary stream format, which is `[length of data in bytes as a 64-bit
     * big-endian binary number][raw binary data]`<br>
     * <br>
     * <b>REALLY BIG WARNING:</b> This class <b>DOES NOT</b> enforce an overall
     * limit on the incoming stream when this constructor is used and
     * {@link #disableLengthLimitsBecauseIAmStreamingAndMyMemoryUseIsNotProportionalToTheStreamLength()}
     * is also called. <b>DO NOT</b> hold onto all the objects that you collect
     * from this class if you call that method, or else <b>YOU COULD BRING DOWN
     * AN APP SERVER OR, POSSIBLY, A POD, AND IT WILL BE YOUR FAULT!</b> The
     * combination of this constructor and that method is meant <b>ONLY</b> for
     * streaming use cases where you don't hold onto objects returned from this
     * class for long at all, and the amount of memory that you use <b>IS
     * NOT</b> proportional to the length of the stream. That is, <b>YOUR
     * IMPLEMENTATION MUST HAVE A MEMORY PROFILE OF O(1)</b> with respect to the
     * incoming stream size.<br>
     * <br>
     * Note that JSON+binary is not supported if the Reader or String based
     * constructors are used to construct this class.<br>
     * <br>
     * When JsonStreamReader is constructed with an InputStream, arrays and
     * objects are not read in for you by default. Instead, it is up to the
     * calling code to loop through the array and object start/separator/end
     * tokens via next(). But if you want to change that, you can call
     * {@link #setRecursiveReadEnabled(boolean)} to enable recursive reads,
     * though binary stream data does not get saved. Note that if you call
     * {@link #getObject()} or {@link #getList()}, you will get the Map or List
     * back regardless of the recursiveRead property. When recursiveRead is
     * false, calls to {@link #getObject()} or {@link #getList()} cause that
     * object or array to get fully consumed to satisfy the request.<br>
     * <br>
     * Note that the JSON string is assumed to be UTF-8 when JsonStreamReader is
     * constructed with an InputStream.
     *
     * @param binaryInput The raw InputStream to read from
     */
    public JsonStreamReader(InputStream binaryInput) {
        this(new Utf8InputStreamReader(binaryInput), binaryInput, null);
        this.recursiveRead = false;
    }

    /**
     * See the warning in {@link #JsonStreamReader(InputStream)}. <b>NEVER</b>,
     * under <b>ANY CIRCUMSTANCE</b>, are you to call this <i>unless</i> if the
     * way that you are using this class causes only O(1) memory to be used with
     * respect to the input stream length. Otherwise, <b>DO NOT CALL THIS
     * METHOD</b>.<br>
     * <br>
     */
    public void disableLengthLimitsBecauseIAmStreamingAndMyMemoryUseIsNotProportionalToTheStreamLength() {
        lengthLimitsEnabled = false;
    }

    private static StringReader createStringReader(String string) {
        if (string == null) {
            throw new JsonParseException("String cannot be null");
        }
        return new StringReader(string);
    }

    public JsonStreamReader(Reader reader) {
        this(reader, null, null);
    }

    public JsonStreamReader(String string) {
        this(string, null);
    }

    public Object getValue() {
        return current;
    }

    /**
     * Returns the current JSON object as a Map&lt;String,Object&gt;<br>
     * <br>
     * Note that if you have recursive reading disabled (see
     * {@link #isRecursiveReadEnabled()}), then calling this method will cause a
     * recursive read to occur on the current object if the current position is
     * at the beginning of an object.
     *
     * @throws JsonStreamParseException Thrown if the current JSON token is not
     *             OBJECT or, for when recursive reading is disabled,
     *             OBJECT_START. When recursive reading is disabled, this
     *             exception can also get thrown if a problem arises while
     *             consuming the object within the input stream
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getObject() {

        // If recursive reading is off and we are at an object start, then go
        // ahead and read in the object recursively
        if (!recursiveRead && currentToken == OBJECT_START) {
            recursiveRead = true;
            try {
                current = readObject();
                currentToken = OBJECT;
            } catch (IOException e) {
                throw new JsonStreamParseException(e);
            } catch (JsonEndOfStreamException e) {
                throw new JsonStreamParseException(e);
            } finally {
                recursiveRead = false;
            }
        }

        assertCurrentToken(OBJECT);
        return (Map<String, Object>) current;
    }

    /**
     * Returns the current JSON array as a List&lt;Object&gt;<br>
     * <br>
     * Note that if you have recursive reading disabled (see
     * {@link #isRecursiveReadEnabled()}), then calling this method will cause a
     * recursive read to occur on the current array if the current position is
     * at the beginning of an array.
     *
     * @throws JsonStreamParseException Thrown if the current JSON token is not
     *             ARRAY or, for when recursive reading is disabled,
     *             ARRAY_START. When recursive reading is disabled, this
     *             exception can also get thrown if a problem arises while
     *             consuming the array within the input stream
     */
    @SuppressWarnings("unchecked")
    public List<Object> getList() {

        // If recursive reading is off and we are at an array start, then go
        // ahead and read in the object recursively
        if (!recursiveRead && currentToken == ARRAY_START) {
            recursiveRead = true;
            try {
                current = readArray();
                currentToken = ARRAY;
            } catch (IOException e) {
                throw new JsonStreamParseException(e);
            } finally {
                recursiveRead = false;
            }
        }

        assertCurrentToken(ARRAY);
        return (List<Object>) current;
    }

    public BigDecimal getNumber() {
        assertCurrentToken(NUMBER);
        return (BigDecimal) current;
    }

    public String getString() {
        assertCurrentToken(STRING);
        return (String) current;
    }

    public boolean getBoolean() {
        assertCurrentToken(BOOLEAN);
        return (Boolean) current;
    }

    /**
     * Returns the current binary input stream as an InputStream. Note that this
     * should be called only by code that has constructed this JsonStreamReader
     * using {@link #JsonStreamReader(InputStream)}. It will always throw a
     * JsonStreamParseException if this JsonStreamReader was constructed any
     * other way.
     */
    public InputStream getBinaryStream() {
        assertCurrentToken(BINARY_STREAM);
        return (InputStream) current;
    }

    /**
     * Returns the number of bytes in the binary stream value. Note that this
     * will always throw a JsonStreamParseException if this JsonStreamReader was
     * not constructed using {@link #JsonStreamReader(InputStream)}.
     */
    public long getBinaryStreamLength() {
        assertCurrentToken(BINARY_STREAM);
        return ((LimitedLengthInputStream) current).getLength();
    }

    public int getCharNum() {
        return charNum;
    }

    public boolean hasNext() throws IOException {
        Character c = null;
        try {
            ignoreWhitespace();
            c = readChar();
            unreadChar(c);
        } catch (Exception e) {
            // We may have hit the end of the stream, which in most cases is a
            // bad thing -- except this case.
        }
        return c != null;
    }

    public JsonConstant next() throws IOException {
        try {
            return next(null);
        } catch (JsonEndOfStreamException e) {
            throw new JsonParseException(e);
        }
    }

    /**
     * get the next token.
     *
     * Note that hint here has 3 useful values:
     * <ul>
     *   <li>FUNCTION_BODY: treat an object as a function body.</li>
     *   <li>STRING: treat most literals as strings (really only for map keys)</li>
     *   <li>LITERAL: look for a proper literal (identifier)</li>
     * </ul>
     *
     * @param hint a hint as to what we want.
     */
    private JsonConstant next(JsonConstant hint) throws IOException, JsonEndOfStreamException {
        // If we have a binary stream, then ensure that it is closed first.
        // Closing it consumes it if it hasn't been
        // fully consumed yet
        if (currentToken == BINARY_STREAM && current instanceof InputStream) {
            ((InputStream) current).close();
        }

        // Skip through all whitespace and comments
        try {
            readWhitespaceAndComments();
        } catch (JsonEndOfStreamException e) {
            return WHITESPACE;
        }
        markPosition(0);
        int startLine = getLineNum();
        int startCol = getColNum();

        // Read and dispatch the next character
        char c = readChar();
        JsonConstant token = JsonConstant.valueOf(c);
        switch (token) {
        case OBJECT_START:
            if (hint == FUNCTION_BODY) {
                current = readFunctionBody();
                token = FUNCTION_BODY;
            }

            // Don't fully read the object if recursion is off
            else if (recursiveRead) {
                current = readObject();
                token = OBJECT;
            }
            break;
        case ARRAY_START:

            // Don't fully read the array if recursion is off
            if (recursiveRead) {
                current = readArray();
                token = ARRAY;
            }
            break;
        case ENTRY_SEPARATOR:
            current = null;
            break;
        case COMMENT_DELIM:
            current = readComment();
            break;
        case MULTICOMMENT_DELIM:
            throw new JsonStreamParseException("Illegal '*' token");
        case LITERAL_START:
            unreadChar(c);
            if (hint == STRING || Character.isJavaIdentifierStart(c)) {
                String result = readLiteralString();
                token = null;
                // FIXME: we should probably check for more key words here.
                if (result.equals("true")) {
                    current = Boolean.TRUE;
                    token = BOOLEAN;
                } else if (result.equals("false")) {
                    current = Boolean.FALSE;
                    token = BOOLEAN;
                } else if (result.equals("function")) {
                    current = readFunction();
                    token = FUNCTION;
                } else if (result.equals("null")) {
                    current = null;
                    token = NULL;
                } else if (result.equals("class")) {
                    // Doh!
                    throw new JsonStreamParseException("Reserved word used as a literal", result);
                } else if (result.equals("Infinity")) {
                    current = Double.POSITIVE_INFINITY;
                    token = NUMBER;
                } else if (result.equals("NaN")) {
                    current = Double.NaN;
                    token = NUMBER;
                }
                if (hint == STRING || hint == LITERAL) {
                    if (token != null) {
                        // Guaranteed to fail.
                        assertTokenType(STRING, token);
                    }
                    current = result;
                    token = STRING;
                }
                if (token == null) {
                    throw new JsonStreamParseException("Invalid literal value", result);
                }
            } else {
                current = readNumber();
                token = NUMBER;
            }
            break;
        case FUNCTION_ARGS_START:
        case FUNCTION_ARGS_END:
            break;
        case QUOTE_SINGLE:
        case QUOTE_DOUBLE:
            current = readString(token, false);
            token = STRING;
            break;
        case BINARY_STREAM: {
            if (binaryInput != null) {
                current = new LimitedLengthInputStream(binaryInput, binaryInput.readLong(), BINARY_STREAM_FINISHED_LISTENER);
            } else {
                throw new JsonStreamParseException(
                        "Binary data encountered in a JsonStreamReader that was not constructed to support binary data");
            }
        }
        default:
        }

        setPosition(startLine, startCol);
        currentToken = token;
        return token;
    }

    private static final BinaryStreamFinishedListener BINARY_STREAM_FINISHED_LISTENER = new BinaryStreamFinishedListener();

    /**
     * Stream-finished listener that consumes the backtick character that is
     * supposed to appear immediately after the end of the JSON binary stream
     */
    private static class BinaryStreamFinishedListener implements StreamFinishedListener {

        @Override
        public void streamFinished(InputStream wrappedStream) throws IOException {

            // Read the next byte. It must be a ` char
            int nextByte = wrappedStream.read();
            if (nextByte != -1 && nextByte != '`') {
                throw new IllegalStateException(
                        "backtick character not encountered at the end of the JSON binary stream");
            }
        }
    }

    /**
     * Returns whether or not recursive reads of objects and arrays are enabled.<br>
     * <br>
     * Note that when this is false, which is the default only when this
     * JsonStreamReader is created with {@link #JsonStreamReader(InputStream)},
     * then reading an object will set the state to OBJECT_START and an array to
     * ARRAY_START instead of OBJECT and ARRAY, respectively. It is then the
     * responsibility of the caller to iterate through the JSON tokens within
     * that object or array with {@link #next()}.<br>
     * <br>
     * When this is true, reading an array or an object will consume that entire
     * array or object and return the current token as ARRAY or OBJECT,
     * respectively.
     */
    public boolean isRecursiveReadEnabled() {
        return this.recursiveRead;
    }

    /**
     * Enables or disables recursive reads of objects and arrays when true or
     * disables that when false.<br>
     * <br>
     * Note that when this is false, which is the default only when this
     * JsonStreamReader is created with {@link #JsonStreamReader(InputStream)},
     * then reading an object will set the state to OBJECT_START and an array to
     * ARRAY_START instead of OBJECT and ARRAY, respectively. It is then the
     * responsibility of the caller to iterate through the JSON tokens within
     * that object or array with {@link #next()}.<br>
     * <br>
     * When this is true, reading an array or an object will consume that entire
     * array or object and return the current token as ARRAY or OBJECT,
     * respectively.
     */
    public void setRecursiveReadEnabled(boolean recursiveRead) {
        this.recursiveRead = recursiveRead;
    }

    private void readWhitespaceAndComments() throws IOException, JsonEndOfStreamException {
        char c;
        do {
            c = readChar();
        } while (Character.isWhitespace(c));

        if (JsonConstant.valueOf(c) == COMMENT_DELIM) {
            // Peek one more to see if this is a comment or not.
            c = readChar();
            unreadChar(c);
            if (JsonConstant.valueOf(c) == COMMENT_DELIM || JsonConstant.valueOf(c) == MULTICOMMENT_DELIM) {
                current = readComment();
                readWhitespaceAndComments();
            } else {
                // Just a slash token, not a comment: restore the slash
                unreadChar(COMMENT_DELIM.getToken());
            }
        } else {
            // We always read one char too many, so step back one.
            unreadChar(c);
        }
    }

    private void ignoreWhitespace() throws IOException, JsonEndOfStreamException {
        char c;
        do {
            c = readChar();
        } while (Character.isWhitespace(c));
        unreadChar(c);
    }

    private Object readObject() throws IOException, JsonEndOfStreamException {
        JsonHandlerProvider provider = getHandlerProvider();
        JsonObjectHandler handler = provider.getObjectHandler();

        JsonConstant token;
        // Hint 'string' so that we parse numbers as strings, this is a little odd, but not unreasonable.
        while ((token = next(STRING)) == STRING) {

            // key
            String key = (String) current;

            // colon
            try {
                token = next();
            } catch (JsonStreamParseException jspe) {
                throw new JsonStreamParseException("Expected ':'", jspe.orig, jspe.line, jspe.col);
            }
            assertTokenType(OBJECT_SEPARATOR, token);

            // value
            setHandlerProvider(provider.getObjectEntryHandlerProvider(key));
            next();
            try {
                handler.put(key, current);
            } catch (JsonValidationException e) {
                throw new JsonStreamParseException(e);
            }
            setHandlerProvider(provider);

            // comma
            token = readComma(OBJECT_END);
            if (token != ENTRY_SEPARATOR) {
                break;
            }
        }

        assertTokenType(OBJECT_END, token);

        return handler.getValue();
    }

    private void setHandlerProvider(JsonHandlerProvider provider) {
        this.provider = provider;
    }

    private JsonHandlerProvider getHandlerProvider() {
        if (provider == null) {
            return defaultProvider;
        }
        return provider;
    }

    private Object readArray() throws IOException {
        JsonHandlerProvider provider = getHandlerProvider();
        JsonArrayHandler handler = provider.getArrayHandler();

        JsonConstant token;
        setHandlerProvider(provider.getArrayEntryHandlerProvider());
        int line = getLineNum();
        int col = getColNum();

        while ((token = next()) != ARRAY_END) {
            if (token == WHITESPACE) {
                // whoops. unterminated array.
                throw new JsonStreamParseException(String.format("Unterminated array at %d:%d", getLineNum(), getColNum()),
                        line, col);
            }
            // Any value
            try {
                handler.add(current);
            } catch (JsonValidationException e) {
                throw new JsonStreamParseException(e.getMessage(), String.valueOf(current), getLineNum(), getColNum(), e);
            }

            // comma
            token = readComma(ARRAY_END);
            if (token != ENTRY_SEPARATOR) {
                break;
            }
        }
        setHandlerProvider(provider);

        assertTokenType(ARRAY_END, token);

        return handler.getValue();
    }

    @Nonnull
    private JsonConstant readComma(@Nonnull JsonConstant alternate) throws IOException {
        JsonConstant token;

        try {
            token = next();
        } catch (JsonStreamParseException jspe) {
            // Ignore this, we were expecting a comma, so let someone know.
            throw new JsonStreamParseException(String.format("Expected ',' or '%s'", alternate.getRepresentation()),
                    jspe.orig, jspe.line, jspe.col);
        }
        if (token != ENTRY_SEPARATOR && token != alternate) {
            throw new JsonStreamParseException(String.format("Expected ',' or '%s', got %s", alternate.getRepresentation(),
                    token.toString()), String.valueOf(current), getLineNum(), getColNum());
        }
        return token;
    }

    private String readString(JsonConstant delim, boolean keepEscapers) throws IOException, JsonEndOfStreamException {
        StringBuilder sb = new StringBuilder();
        boolean isEscaped = false;
        markPosition(1);
        try {
            while (true) {
                char c = readChar();
                if (c == delim.getToken()) {
                    if (!isEscaped) {
                        // We consume the delimiter and call it a day.
                        break;
                    }
                }
                if (!isEscaped && c == '\n') {
                    throw new JsonStreamParseException("Unterminated string", sb.toString(), getLineNum(), getColNum());
                }
                // A backslash might togle isEscaped; anything else clears it.
                if (c == '\\') {
                    if (keepEscapers) {
                        isEscaped = !isEscaped;
                    } else {
                        c = readEscapedChar();
                    }
                } else {
                    isEscaped = false;
                }
                sb.append(c);
            }
        } catch (JsonEndOfStreamException e) {
            throw new JsonStreamParseException("Unterminated string", sb.toString(), getLineNum(), getColNum());
        }
        return sb.toString();
    }

    /**
     * This method consumes what we expect to be a comment. It should be called
     * after consuming the '/' character, but before either the second '/' or
     * the '*'. If the next character read is <i>not</i> one of those two (i.e.
     * this is not a comment), it will throw a {@link JsonStreamParseException}.
     *
     * @return a {@link JsComment} with the comment text and location
     * @throws IOException
     * @throws JsonEndOfStreamException if there is no next character
     * @throws JsonStreamParseException if the input does not begin with a valid
     *             comment, or if a multi-line comment is not closed.
     */
    private JsComment readComment() throws IOException, JsonEndOfStreamException {
        markPosition(0);
        char delim = readChar();
        boolean isMulti = false;

        if (delim == COMMENT_DELIM.getToken()) {
            isMulti = false;
        } else if (delim == MULTICOMMENT_DELIM.getToken()) {
            isMulti = true;
        } else {
            throw new JsonStreamParseException(String.format("Current Token is %s, not %s", delim, MULTICOMMENT_DELIM));
        }

        StringBuilder sb = new StringBuilder();
        char c;
        try {
            while (true) {
                c = readChar();
                if (!isMulti && c == '\n') {
                    break;
                } else if (isMulti && c == MULTICOMMENT_DELIM.getToken()) {
                    char c2 = readChar();
                    if (c2 == COMMENT_DELIM.getToken()) {
                        break;
                    } else {
                        unreadChar(c2);
                    }
                }
                sb.append(c);
            }
        } catch (JsonEndOfStreamException e) {
            if (isMulti) {
                // We finished the stream before reaching end-of-comment!
                throw new JsonStreamParseException("Unclosed comment");
            }
        }
        return new JsComment(sb.toString(), getLineNum(), getColNum());
    }

    private JsFunction readFunction() throws IOException, JsonEndOfStreamException {
        int line = lineNum;
        int col = colNum;
        // We hint 'literal' to let the parser know that arbitrary literals are ok.
        JsonConstant next = next(LITERAL);
        String functionName = null;
        if (next == STRING) {
            functionName = (String) current;
            next = next();
        }
        assertTokenType(FUNCTION_ARGS_START, next);

        List<String> args = Lists.newArrayList();

        JsonConstant token;
        while (true) {
            token = next(LITERAL);
            if (token == FUNCTION_ARGS_END) {
                if (args.size() != 0) {
                    throw new JsonStreamParseException("Unexpected comma before ')'");
                }
                break;
            }

            // Literal Strings only
            assertTokenType(STRING, token);
            args.add((String) current);

            // comma
            token = readComma(FUNCTION_ARGS_END);
            if (token != ENTRY_SEPARATOR) {
                break;
            }
        }

        assertTokenType(FUNCTION_ARGS_END, token);

        assertTokenType(JsonConstant.FUNCTION_BODY, next(FUNCTION_BODY));

        return new JsFunction(functionName, args, (String) current, line, col);
    }

    private String readFunctionBody() throws IOException, JsonEndOfStreamException {
        // We have to have a bit of a stack in order to find the end of function
        // body properly.
        ArrayDeque<JsonConstant> stack = new ArrayDeque<>();
        StringBuilder sb = new StringBuilder();
        char c;
        JsonConstant token;
        LOOP: while (true) {
            c = readChar();
            token = JsonConstant.valueOf(c);
            switch (token) {
            case OBJECT_START:
                stack.push(token);
                break;
            case OBJECT_END:
                if (stack.isEmpty()) {
                    // End of function
                    break LOOP;
                } else {
                    JsonConstant peek = stack.peek();
                    if (peek == OBJECT_START) {
                        stack.pop();
                    }
                }
                break;
            case QUOTE_DOUBLE:
            case QUOTE_SINGLE:
                sb.append(c);
                sb.append(readString(token, true));
                break;
            case COMMENT_DELIM:
                char delim = readChar();
                unreadChar(delim);
                if (delim == COMMENT_DELIM.getToken() || delim == MULTICOMMENT_DELIM.getToken()) {
                    readComment();
                    c = '\n';
                    break;
                }
            default:
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private Number readNumber() throws IOException, JsonEndOfStreamException {
        StringBuilder sb = new StringBuilder();
        while (hasNext()) {
            char c = readChar();
            JsonConstant token = JsonConstant.valueOf(c);
            if ((!token.equals(LITERAL_START))) {
                unreadChar(c);
                break;
            } else {
                sb.append(c);
            }
        }
        BigDecimal ret;
        try {
            String s = sb.toString();
            if (s.equals("NaN")) {
                return Double.NaN;
            } else {
                boolean negative = s.charAt(0) == '-';
                if ("Infinity".equals(negative ? s.substring(1) : s)) {
                    return negative ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
                }
            }
            ret = new BigDecimal(sb.toString());

        } catch (NumberFormatException e) {
            throw new JsonStreamParseException("Could not parse a number", sb.toString(), getLineNum(), getColNum(), e);
        }
        return ret;
    }

    /**
     * Literal Strings can only be used as keys in maps, so the entry separator
     * and whitespace chars are treated as the delimiters.
     *
     * @return
     * @throws IOException
     */
    private String readLiteralString() throws IOException, JsonEndOfStreamException {
        StringBuilder sb = new StringBuilder();
        try {
            while (true) {
                char c = readChar();
                JsonConstant token = JsonConstant.valueOf(c);
                if (token != LITERAL_START) {
                    unreadChar(c);
                    break;
                } else if (c == '\\') {
                    c = readEscapedChar();
                }
                sb.append(c);
            }
        } catch (JsonEndOfStreamException eof) {
            // ignore, just return what we have.
        }
        return sb.toString();
    }

    private char readChar() throws IOException, JsonEndOfStreamException {
        int i = reader.read();

        if (i == -1) {
            throw new JsonEndOfStreamException("End of stream unexpectedly reached.");
        }

        charNum++;

        // If we have a binary input stream and if length limits were disabled,
        // then don't validate the length because
        // we are streaming in, and memory use is supposed to be O(1) with
        // respect to the stream length by callers
        // that are using streaming
        if (charNum > MAX_LENGTH && lengthLimitsEnabled) {
            throw new JsonStreamParseException("Input too long.");
        }

        char c = (char) i;

        if (c == '\n') {
            prevColNum = colNum;
            lineNum++;
            colNum = 1;
        } else {
            colNum++;
        }

        return c;
    }

    private void unreadChar(char c) throws IOException {
        reader.unread(c);
        charNum--;
        if (c == '\n') {
            colNum = prevColNum;
            lineNum--;
        } else {
            colNum--;
        }
    }

    private char readEscapedChar() throws IOException, JsonEndOfStreamException {
        char c = readChar();

        if (c == 'u') {
            c = readUnicodeEscapedChar();
        } else {
            Character esc = escapes.get(c);
            if (esc == null) {
                throw new JsonStreamParseException(String.format("Unknown escape sequence : \\%c", c));
            } else {
                c = esc;
            }
        }
        return c;
    }

    private char readUnicodeEscapedChar() throws IOException, JsonEndOfStreamException {

        // Read the next 4 hex digits.
        int line = lineNum;
        int col = colNum;
        StringBuilder sb = new StringBuilder(4);
        for (int i = 0; i < 4; i++) {
            sb.append(readChar());
        }
        char c;
        try {
            c = (char) Integer.parseInt(sb.toString(), 16);
        } catch (NumberFormatException e) {
            throw new JsonStreamParseException(e.getMessage(), sb.toString(), line, col, e);
        }
        return c;
    }

    public void close() throws IOException {
        reader.close();
    }

    private void assertTokenType(JsonConstant expected, JsonConstant actual) {
        if (expected != actual) {
            throw new JsonStreamParseException(String.format("Expected '%s', found '%s'", expected.getRepresentation(), actual.getRepresentation()));
        }
    }

    private void assertCurrentToken(JsonConstant expected) {
        if (currentToken != expected) {
            throw new JsonStreamParseException(String.format("Current Token is '%s', not '%s'", currentToken.getRepresentation(), expected.getRepresentation()));
        }
    }

    private void setPosition(int line, int col) {
        lastLineNum = line;
        lastColNum = col;
    }

    private void markPosition(int offset) {
        lastLineNum = lineNum;
        lastColNum = colNum-offset;
    }

    public int getLineNum() {
        return lastLineNum;
    }

    private int getColNum() {
        return lastColNum;
    }

    /**
     * Runtime exception used to indicate improperly formatted json input. All
     * messages will be decorated with the line and column numbers of the
     * current position in the reader.
     */
    public class JsonStreamParseException extends JsonParseException {

        private static final long serialVersionUID = -455507772693955451L;
        public final int line;
        public final int col;
        public final String orig;

        public JsonStreamParseException(String msg, String orig, int line, int col, Throwable cause) {
            super(((orig == null)?String.format("%s [%d, %d]", msg, line, col)
                    :String.format("%s [%d, %d]: '%s'", msg, line, col, orig)), cause);
            this.line = line;
            this.col = col;
            this.orig = orig;
        }

        public JsonStreamParseException(String msg, String orig, int line, int col) {
            this(msg, orig, line, col, null);
        }

        public JsonStreamParseException(String msg, int line, int col) {
            this(msg, null, line, col, null);
        }

        public JsonStreamParseException(String msg, String orig) {
            this(msg, orig, getLineNum(), getColNum(), null);
        }

        public JsonStreamParseException(String msg) {
            this(msg, null, getLineNum(), getColNum(), null);
        }

        public JsonStreamParseException(Throwable cause) {
            this(cause.getMessage(), (current == null)?null:String.valueOf(current), getLineNum(), getColNum(), cause);
        }
    }

    private static class JsonEndOfStreamException extends Exception {

        private static final long serialVersionUID = -9041608991522310436L;

        public JsonEndOfStreamException(String msg) {
            super(msg);
        }
    }

    /**
     * Runtime exception used to indicate improperly formatted json input. All
     * messages will be decorated with the line and column numbers of the
     * current position in the reader.
     */
    public static class JsonParseException extends RuntimeException {

        private static final long serialVersionUID = -8902652335128509063L;

        public JsonParseException(String message) {
            super(message);
        }

        public JsonParseException(String message, Throwable cause) {
            super(message, cause);
        }

        public JsonParseException(Throwable cause) {
            super(cause.getMessage(), cause);
        }
    }
}
