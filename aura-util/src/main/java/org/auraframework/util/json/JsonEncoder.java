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

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TimeZone;

import javax.annotation.Nonnull;

import org.auraframework.util.AuraTextUtil;
import org.auraframework.util.UncloseableOutputStream;
import org.json.JSONObject;

import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.CountingOutputStream;

/**
 * java -> javascript encoder.
 *
 * May or may not follow the official JSON (JavaScript Object Notation)
 * standard. It handles serializing all the basics, numbers, strings, booleans,
 * arrays, and maps as well as some common SFDC data structures like
 * PicklistItem and anything that implements {@link JsonSerializable}. Some
 * significant consumers of the output are inline editing
 * <ol>
 * <li>Java null reference: JS null value
 * <li>Java Map: JS Object
 * <li>Java List: JS Array
 * <li>Java Object: object.toArray()
 * </ol>
 *
 * NOTE: the code for handling the stacks is rather more complicated to maintain
 * performance. The problem is that {@link #writeMapBegin()} and
 * {@link #writeArrayBegin()} are called hundreds of thousands of times a
 * second, meaning that creating and discarding objects for each one is too
 * expensive. This means that we use a separate stack in the case that we are
 * not formatting to avoid the allocation of the object.
 *
 * @see <a
 *      href="https://sites.google.com/a/salesforce.com/user-interface/documentation/json">SFDC
 *      json documentation</a>
 * @since 144
 */
public class JsonEncoder implements Json {
    @SuppressWarnings("serial")
    public static class JsonException extends RuntimeException {
        public JsonException(String message) {
            super(message);
        }
    }

    public static final String MIME_TYPE = "application/json";

    /**
     * A class to track indents when formatting json.
     */
    private static class IndentEntry {
        public IndentEntry(IndentType type, String indent) {
            this.type = type;
            this.first = true;
            this.indent = indent + type.getIndent();
        }

        private final IndentType type;
        private boolean first;
        private final String indent;

        public boolean needSeparator() {
            if (!this.type.isSeparated()) {
                throw new JsonException("Cannot use separator on " + this.type);
            }
            if (this.first) {
                this.first = false;
                return false;
            }
            return true;
        }

        public String getIndent() {
            return this.indent;
        }

        public IndentType getType() {
            return this.type;
        }
    }
    
    /**
     * An Appendable wrapper class that provides the optional functionality to capture a
     * subsequence of the serialized content, via {@link JsonEncoder#startCapturing()}
     * and {@link JsonEncoder#stopCapturing()}, for the purpose of cacheing serialization
     * results. Any write* call should be appending to the encoder's CapturableAppendable,
     * so that if capturing is turned on, any content is written not only to the main
     * Appendable, but also to the current capturing buffer.
     *
     * @author helen.kwong
     * @since 214
     */
    private static class CapturableAppendable implements Appendable {
        
        // the "main" Appendable that is always written to
        private final Appendable all;
        // the optional/capturing Appendable that is non-empty only if capturing is in progress
        private Deque<StringBuilder> captured = new ArrayDeque<>();
        
        CapturableAppendable(Appendable out) {
            this.all = out;
        }
        
        @Override
        public Appendable append(CharSequence csq) throws IOException {
            all.append(csq);
            if (!captured.isEmpty()) {
                captured.getFirst().append(csq);
            }
            return this;
        }

        @Override
        public Appendable append(CharSequence csq, int start, int end) throws IOException {
            all.append(csq, start, end);
            if (!captured.isEmpty()) {
                captured.getFirst().append(csq, start, end);
            }
            return this;
        }

        @Override
        public Appendable append(char c) throws IOException {
            all.append(c);
            if (!captured.isEmpty()) {
                captured.getFirst().append(c);
            }
            return this;
        }
        
        /**
         * Initializes the capturing buffer
         */
        void startCapturing() {
            captured.addFirst(new StringBuilder());
        }
        
        /**
         * Stops capturing -- returns the result of what has been captured in
         * the buffer and resets the buffer.
         * @return captured string, or null if capturing isn't turned on
         */
        String stopCapturing() {
            String result = null;
            if (!captured.isEmpty()) {
                result = captured.removeFirst().toString();
                if (!captured.isEmpty()) {
                    captured.getFirst().append(result);
                }
            }
            return result;
        }
    }

    private final JsonSerializationContext serializationContext;
    private final Appendable out;
    private final CapturableAppendable cacheableOut;
    private final ArrayDeque<IndentEntry> indentStack = new ArrayDeque<>();
    private final DataOutputStream binaryOutput;
    private CountingOutputStream currentBinaryStream;
    private long currentBinaryStreamLength;

    /**
     * Create a Json Serialization context object that maintains information
     * about one run. This Object is NOT thread-safe. It should only be used by
     * one thread at a time, and should not be reused.
     *
     * @param out The Appendable to write the serialized objects to.
     * @param format defaults to false. If true, the output will be multi-line
     *            and indented.
     */
    public JsonEncoder(Appendable out, boolean format) {
        this(out, null, new DefaultJsonSerializationContext(format, false));
    }

    public JsonEncoder(Appendable out, JsonSerializationContext context) {
        this(out, null, context);
    }

    /**
     * @deprecated refSupport no longer supported
     */
    @Deprecated
    public JsonEncoder(Appendable out, boolean format, boolean refSupport) {
        this(out, format);
    }

    protected JsonEncoder(Appendable out, OutputStream binaryOutput, JsonSerializationContext context) {
        this.out = out;
        this.cacheableOut = new CapturableAppendable(out);
        this.serializationContext = context;

        // Set binaryOutput to a DataOutputStream if applicable; otherwise, null
        this.binaryOutput = binaryOutput == null ? null
                : (binaryOutput instanceof DataOutputStream ? (DataOutputStream) binaryOutput : new DataOutputStream(
                        binaryOutput));
    }

    /**
     * Following are a bunch of static serialize methods. They mainly exist in
     * order to size a StringBuilder for you to some reasonable size.
     */
    public static String serialize(Object obj) {
        StringBuilder sb = new StringBuilder(16);
        serialize(obj, sb);
        return sb.toString();
    }

    /**
     * @param obj The thing to serialize
     * @param out The destination for the serialized form
     * @param format true if output should be indented and multiline for human readability (default = false)
     * @throws JsonSerializationException if there's an issue during serialization
     */
    public static void serialize(Object obj, Appendable out, boolean format) {
        try {
            new JsonEncoder(out, format).writeValue(obj);
        } catch (IOException e) {
            throw new JsonSerializationException(e);
        }
    }

    /**
     * @deprecated refSupport no longer supported
     *
     * @param obj The thing to serialize
     * @param out The destination for the serialized form
     * @param format true if output should be indented and multiline for human readability (default = false)
     * @param refSupport refSupport no longer supported
     * @throws JsonSerializationException if there's an issue during serialization
     */
    @Deprecated
    public static void serialize(Object obj, Appendable out, boolean format, boolean refSupport) {
        serialize(obj, out, format);
    }

    public static String serialize(Object obj, boolean format) {
        StringBuilder sb = new StringBuilder(16);
        serialize(obj, sb, format);
        return sb.toString();
    }

    /**
     * @deprecated refSupport no longer supported
     *
     * @param obj The thing to serialize
     * @param format true if output should be indented and multiline for human readability (default = false)
     * @param refSupport refSupport is no longer supported
     * @return object serialized to string
     */
    @Deprecated
    public static String serialize(Object obj, boolean format, boolean refSupport) {
        return serialize(obj, format);
    }

    public static void serialize(Object obj, Appendable out) {
        serialize(obj, out, false);
    }

    public static void serialize(Object obj, Appendable out, JsonSerializationContext context) {
        try {
            new JsonEncoder(out, null, context).writeValue(obj);
        } catch (IOException e) {
            throw new JsonSerializationException(e);
        }
    }

    public static String serialize(Object obj, JsonSerializationContext context) {
        try {
            StringBuilder sb = new StringBuilder(16);
            new JsonEncoder(sb, null, context).writeValue(obj);
            return sb.toString();
        } catch (IOException e) {
            throw new JsonSerializationException(e);
        }
    }

    public static String serialize(Object[] result) {
        StringBuilder sb = new StringBuilder(result.length * 16);
        serialize(result, sb);
        return sb.toString();
    }

    public static String serialize(Collection<?> result) {
        StringBuilder sb = new StringBuilder(result.size() * 16);
        serialize(result, sb);
        return sb.toString();
    }

    public static String serialize(Map<String, ?> result) {
        StringBuilder sb = new StringBuilder(result.size() * 32);
        serialize(result, sb);
        return sb.toString();
    }

    /**
     * Creates a Json instance that is suitable for output streaming, one
     * element at a time. This can help avoid building up an entire JavaScript
     * AST all in memory before it gets serialized, which can help cut down
     * memory use.<br>
     * <br>
     * Note that you will need to call {@link #close()} when you are done to
     * ensure that all characters have been written out to the given
     * OutputStream. Otherwise, some characters might be missing at the end.
     *
     * @param out The OutputStream to write the serialized objects to using
     *            UTF-8. This must not be null.
     * @param format Defaults to false. If true, the output will be multi-line
     *            and indented.
     * @param nullValues When true, null values are written out when they exist
     *            in arrays and map values. When false, array items and map
     *            entries with null values are not serialized
     * @return A new Json instance that you can use for streaming to the given
     *         OutputStream
     */
    public static JsonEncoder createJsonStream(@Nonnull OutputStream out, boolean format,
                                               boolean nullValues) {
        return createJsonStream(out, new DefaultJsonSerializationContext(format, nullValues));
    }

    /**
     * @deprecated refSupport no longer supported
     */
    @Deprecated
    public static JsonEncoder createJsonStream(@Nonnull OutputStream out, boolean format, boolean refSupport,
            boolean nullValues) {
        return createJsonStream(out, format, nullValues);
    }

    /**
     * Creates a Json instance that is suitable for output streaming, one
     * element at a time. This can help avoid building up an entire JavaScript
     * AST all in memory before it gets serialized, which can help cut down
     * memory use.<br>
     * <br>
     * Note that you will need to call {@link #close()} when you are done to
     * ensure that all characters have been written out to the given
     * OutputStream. Otherwise, some characters might be missing at the end.
     *
     * @param out The OutputStream to write the serialized objects to using
     *            UTF-8. This must not be null.
     * @param context The JSON serialization context to use for output
     * @return A new Json instance that you can use for streaming to the given
     *         OutputStream
     **/
    public static JsonEncoder createJsonStream(@Nonnull OutputStream out, JsonSerializationContext context) {
        if (out == null) {
            throw new IllegalArgumentException("out must not be null");
        }
        final Writer writer = new OutputStreamWriter(out, Charsets.UTF_8);
        return new JsonEncoder(writer, out, context);
    }

    /**
     * Creates a Json instance that is suitable for output streaming, one
     * element at a time. This can help avoid building up an entire JavaScript
     * AST all in memory before it gets serialized, which can help cut down
     * memory use.<br>
     *
     * @param out The Appendable to which to write the serialized objects. This must not be null.
     * @param context The JSON serialization context to use for output
     * @return A new Json instance that you can use for streaming to the given appendable
     **/
    public static JsonEncoder createJsonStream(@Nonnull Appendable out, JsonSerializationContext context) {
        return new JsonEncoder(out, null, context);
    }

    /**
     * This method is essentially here to provide type-checking for the
     * outermost map.
     *
     * @param jsonMap
     * @param out
     * @throws JsonSerializationException if there's an issue during
     *             serialization
     */
    public static void serialize(Map<String, ?> jsonMap, Appendable out) {
        serialize((Object) jsonMap, out);
    }

    /**
     * Push an indent, with a type.
     *
     * See the notes on performance on the class above.
     *
     * This either creates a new IndentEntry and pushes a value on the boolean
     * stack, or it just uses the boolean stack (in the case of not pretty
     * printing).
     *
     * @param type the type of indent to push.
     */
    @Override
    public void pushIndent(IndentType type) {
        if (this.indentStack.isEmpty()) {
            this.indentStack.push(new IndentEntry(type, ""));
        } else {
            this.indentStack.push(new IndentEntry(type, this.indentStack.peek().getIndent()));
        }
    }

    /**
     * Pop an indent off the stack.
     *
     * This both checks the type on the stack, and pulls it off. See the notes
     * on performance on the class above.
     */
    @Override
    public void popIndent(IndentType type, String message) {
        if (this.indentStack.isEmpty()) {
            throw new JsonException("Empty indent stack: " + message);
        }
        if (!type.equals(this.indentStack.pop().getType())) {
            throw new JsonException("Mismatched indent stack: " + message);
        }
    }

    /**
     * get the current indent.
     *
     * See the notes on performance on the class above.
     */
    @Override
    public String getIndent() {
        if (this.indentStack.isEmpty()) {
            return "";
        } else {
            return this.indentStack.peek().getIndent();
        }
    }

    /**
     * If formatting is enabled, indent, otherwise, no-op.
     *
     * @throws IOException
     */
    @Override
    public void writeIndent() throws IOException {
        if (isFormatting()) {
            cacheableOut.append(getIndent());
        }
    }

    /**
     * Write the beginning of a map. Make sure to call writeMapEnd later on.
     *
     * @throws IOException
     */
    @Override
    public void writeMapBegin() throws IOException {
        cacheableOut.append('{');
        writeBreak();
        pushIndent(IndentType.BRACE);
    }

    /**
     * Write the end of a map.
     *
     * @throws IOException
     */
    @Override
    public void writeMapEnd() throws IOException {
        writeBreak();
        popIndent(IndentType.BRACE, "Json.writeMapBegin must be called before calling Json.writeMapEnd");
        writeIndent();
        cacheableOut.append('}');
    }

    /**
     * Write the beginning of an array. Make sure to call writeArrayEnd later
     * on.
     *
     * @throws IOException
     */
    @Override
    public void writeArrayBegin() throws IOException {
        cacheableOut.append('[');
        writeBreak();
        pushIndent(IndentType.SQUARE);
    }

    /**
     * Write the end of an array.
     *
     * @throws IOException
     */
    @Override
    public void writeArrayEnd() throws IOException {
        writeBreak();
        popIndent(IndentType.SQUARE, "Json.writeArrayBegin must be called before calling Json.writeArrayEnd");
        writeIndent();
        cacheableOut.append(']');
    }

    /**
     * If any entries have already been written to the current map/array (as
     * marked by the write*Begin methods), write a comma. If no elements have
     * yet been written, no-op.
     *
     * @throws IOException
     */
    @Override
    public void writeComma() throws IOException {
        if (!this.indentStack.isEmpty()) {
            if (this.indentStack.peek().needSeparator()) {
                cacheableOut.append(",");
                // Special handling of pretty print for collections (arrays and objects)
                // to separate the items on individual lines.
                if (isFormatting() || isFormattingRootItems()) {
                    cacheableOut.append('\n');
                }
            }
        } else {
            // ooh, why did this happen?
            throw new JsonException("writeComma with no writeArrayBegin or writeMapBegin");
        }
    }

    @Override
    public void writeMapSeparator() throws IOException {
        cacheableOut.append(':');
    }

    /**
     * Encode the given value and if m != null then perform Aura-specific
     * seialization that outputs extra information in the stream so that
     * references can be established by the JSON reader
     */
    @Override
    public void writeValue(Object value) throws IOException {
        JsonSerializer<Object> serializer = serializationContext.getSerializer(value);
        if (serializer == null) {
            throw new JsonSerializerNotFoundException(value);
        }
        serializer.serialize(this, value);
    }

    /**
     * Just write the value.toString() out. Does not quote the value.
     *
     * @param value
     * @throws IOException
     */
    @Override
    public void writeLiteral(Object value) throws IOException {
        if (value == null) {
            cacheableOut.append("null");
        } else {
            cacheableOut.append(value.toString());
        }
    }

    /**
     * Quotes value.toString() and writes it.
     *
     * @param value
     * @throws IOException
     */
    @Override
    public void writeString(Object value) throws IOException {
        if (value == null) {
            cacheableOut.append("null");
            return;
        }

        cacheableOut.append(JSONObject.quote(AuraTextUtil.escapeForJSONString(value.toString())));
    }

    /**
     * Write the date in the ISO-8601 format that's semi-standard in json2 (in
     * that it's in the comments)
     *
     * @param value
     * @throws IOException
     */
    @Override
    public void writeDate(Date value) throws IOException {
        cacheableOut.append('"');
        // Use the ISO DateTime format to write the date.
        synchronized (ISO8601FORMAT) {
            cacheableOut.append(ISO8601FORMAT.format(value));
        }
        cacheableOut.append('"');
    }

    private static final SimpleDateFormat ISO8601FORMAT;
    static {
        ISO8601FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        ISO8601FORMAT.setTimeZone(TimeZone.getTimeZone("GMT"));
    }

    /**
     * Write a map in a predictable order
     *
     * @param map
     * @throws IOException
     */
    @Override
    public void writeMap(Map<?, ?> map) throws IOException {
        writeMapBegin();
        for (Object o : map.entrySet()) {
            Map.Entry<?, ?> entry = (Map.Entry<?, ?>) o;
            Object value = entry.getValue();
            writeMapEntry(entry.getKey(), value);
        }
        writeMapEnd();
    }

    /**
     * Write an array
     *
     * @param array
     * @throws IOException
     */
    @Override
    public void writeArray(Object[] array) throws IOException {
        writeArrayBegin();
        for (Object o : array) {
            writeArrayEntry(o);
        }
        writeArrayEnd();
    }

    /**
     * Write an array
     *
     * @param array
     * @throws IOException
     */
    @Override
    public void writeArray(Collection<?> array) throws IOException {
        writeArrayBegin();
        for (Object o : array) {
            writeArrayEntry(o);
        }
        writeArrayEnd();
    }

    /**
     * Write a value into the current array, and add leading commas and
     * formatting as appropriate.
     *
     * @param value
     * @throws IOException
     */
    @Override
    public void writeArrayEntry(Object value) throws IOException {
        if (value != null || serializationContext.isNullValueEnabled()) {
            writeComma();
            writeIndent();
            writeValue(value);
        }
    }

    /**
     * Write a value into the current Map, and add leading commas and formatting
     * as appropriate.
     *
     * @param key
     * @param value
     * @throws IOException
     */
    @Override
    public void writeMapEntry(Object key, Object value) throws IOException {
        writeMapEntry(key, value, null);
    }

    /**
     * Write a value into the current Map, and add leading commas and formatting
     * as appropriate.  This version will consult its {@code type} parameter to
     * decide how to serialize null maps and arrays.
     *
     * @param key
     * @param value
     * @param type
     * @throws IOException
     */
    @Override
    public void writeMapEntry(Object key, Object value, String type) throws IOException {
        if (value == null && type != null) {
            try {
                Class<?> valueClass = JsonEncoder.class.getClassLoader().loadClass(type.substring("java://".length()));
                if (Iterable.class.isAssignableFrom(valueClass)) {
                    value = new ArrayList<Boolean>(0);
                } else if (Map.class.isAssignableFrom(valueClass)) {
                    value = new HashMap<String,String>(0);
                }
            } catch (ClassNotFoundException e) {
                // Nevermind; treat "we don't know" as a non-list, non-map
            }
        }
        if (value != null || serializationContext.isNullValueEnabled()) {
            writeMapKey(key);
            writeValue(value);
        }
    }

    /**
     * Write a partial Map Entry -- everything except the value. This is useful
     * when the value requires special serialization.
     *
     * @param key
     * @throws IOException
     * @throws JsonSerializerNotFoundException if a serializer is not found for the key
     */
    @Override
    public void writeMapKey(Object key) throws IOException {
        writeComma();
        writeIndent();
        JsonSerializer<Object> serializer = serializationContext.getSerializer(key);
        if (serializer == null) {
            throw new JsonSerializerNotFoundException(key);
        }
        if (key == null) {
            cacheableOut.append("\"null\"");
        } else {
            this.writeString(key);
        }
        writeMapSeparator();
    }

    /**
     * If formatting is on, or if at top level, write out a line break.
     *
     * @throws IOException
     */
    @Override
    public void writeBreak() throws IOException {
        if (isFormatting()) {
            cacheableOut.append('\n');
        }
    }

    /**
     * Start a binary stream using the given length and return an OutputStream
     * that the caller can write its binary data to.<br>
     * <br>
     * After calling this, write exactly the number of bytes specified to the
     * OutputStream returned by this method. After you do that, call
     * {@link #writeBinaryStreamEnd()}.
     *
     * @param streamLength The number of bytes that will exist in the output before the ending backtick
     * @return The OutputStream that the caller can write its output to
     */
    @Override
    public OutputStream writeBinaryStreamBegin(long streamLength) throws IOException {

        // If we are in the middle of another binary stream, then complain
        if (currentBinaryStream != null) {
            throw new IllegalStateException("Previous binary stream was not ended");
        }

        // Signal our binary stream's beginning
        validateBinaryStreamEnabledAndWriteBacktick();

        // Flush the output stream writer to push all pending characters onto the OutputStream
        if (out instanceof Writer) {
            ((Writer) out).flush();
        }

        // A JSON+binary stream begins with the length as a big endian 64-bit long
        binaryOutput.writeLong(streamLength);
        currentBinaryStreamLength = streamLength;

        // Wrap our binaryOutput in a CountingOutputStream so that we can
        // validate the length later
        return currentBinaryStream = new CountingOutputStream(new UncloseableOutputStream(binaryOutput));
    }

    private void validateBinaryStreamEnabledAndWriteBacktick() throws IOException {
        if (binaryOutput == null) {
            throw new IllegalStateException(
                    "Binary streams are supported only when Json.createJsonStream is used with an InputStream");
        }
        cacheableOut.append('`');
    }

    /**
     * Ends the current binary stream and ensures that the correct number of
     * bytes were written. If a discrepancy exists, then an
     * IllegalStateException gets thrown.
     */
    @Override
    public void writeBinaryStreamEnd() throws IOException {

        // Ensure that we are in a binary stream, and validate the length
        if (currentBinaryStream == null) {
            throw new IllegalStateException("Binary stream was not started");
        }
        if (currentBinaryStreamLength != currentBinaryStream.getCount()) {
            throw new IllegalStateException("Length of the binary stream was written out as "
                    + currentBinaryStreamLength + " bytes, but " + currentBinaryStream.getCount()
                    + " bytes were actually written to the OutputStream returned by writeBinaryStreamBegin()");
        }

        // Signal our binary stream's ending
        validateBinaryStreamEnabledAndWriteBacktick();
        currentBinaryStream = null;
        currentBinaryStreamLength = 0;
    }

    /**
     * Writes out any buffered characters in the OutputStreamWriter to the
     * binary OutputStream and then closes the OutputStream.<br>
     * <br>
     * Note that this method does nothing if Json was not created with an
     * OutputStream, such as via
     * {@link #createJsonStream(OutputStream, boolean, boolean)}.
     */
    @Override
    public void close() throws IOException {
        if (binaryOutput != null) {
            if (out instanceof Writer) {

                // This also closes the underlying OutputStream
                ((Writer) out).close();
            } else {
                binaryOutput.close();
            }
        }
    }

    /**
     * Note: You should always try to use the write* methods instead, if at all
     * possible.
     *
     * @return the appendable for this run in case you want to write something
     *         special to it.
     */
    @Override
    public Appendable getAppendable() {
        return out;
    }
    
    @Override
    public void startCapturing() {
        cacheableOut.startCapturing();
    }
    
    @Override
    public String stopCapturing() {
        return cacheableOut.stopCapturing();
    }

    private boolean isFormatting() {
        return serializationContext.format();
    }

    private boolean isFormattingRootItems() {
        // Pretty print of collections has been requested and we are at level 1,
        // which means the items of the root collection.
        return serializationContext.formatRootItems() && indentStack.size() == 1;
    }

    @Override
    public JsonSerializationContext getSerializationContext() {
        return this.serializationContext;
    }

    /**
     * Resolve references and remove refId/serRefIds from the passed in object.
     * Useful when parsing json serialized with reference support by this class.
     *
     * @param config Must be a Map or List that consists only of other
     *            Maps/Lists and primitives
     * @return A Map or List representing the data passed in with its references
     *         resolved
     */
    public static Object resolveRefs(Object config) {
        return resolveRefs(config, Maps.<Integer, Object> newHashMap(), null);
    }

    private static Object resolveRefs(Object config, Map<Integer, Object> cache) {
        return resolveRefs(config, cache, null);
    }

    @SuppressWarnings("unchecked")
    private static Object resolveRefs(Object config, Map<Integer, Object> cache, Object newValue) {
        if (config instanceof List) {
            List<Object> l = (List<Object>) config;
            List<Object> result;
            if (newValue != null) {
                result = (List<Object>) newValue;
            } else {
                result = Lists.newArrayListWithExpectedSize(l.size());
            }
            for (Object o : l) {
                result.add(resolveRefs(o, cache));
            }
            return result;
        } else if (config instanceof Map) {
            Map<String, Object> m = (Map<String, Object>) config;
            BigDecimal serId = (BigDecimal) m.get(ApplicationKey.SERIAL_ID.toString());
            if (serId != null) {
                Object value = m.get(ApplicationKey.VALUE.toString());
                Object result = value instanceof List ? Lists.newArrayList() : Maps.newHashMap();
                // We must cache the new item first because we could loop back
                // to this serId internally
                cache.put(serId.intValue(), result);
                return resolveRefs(value, cache, result);
            }
            BigDecimal serRefId = (BigDecimal) m.get(ApplicationKey.SERIAL_REFID.toString());
            if (serRefId != null) {
                Object value = cache.get(serRefId.intValue());
                // if there is no value we could throw here
                return value;
            }

            Map<String, Object> result;
            if (newValue != null) {
                result = (Map<String, Object>) newValue;
            } else {
                result = Maps.newHashMapWithExpectedSize(m.size());
            }
            for (Entry<String, Object> e : m.entrySet()) {
                result.put(e.getKey(), resolveRefs(e.getValue(), cache));
            }
            return result;
        }
        return config;
    }
}
