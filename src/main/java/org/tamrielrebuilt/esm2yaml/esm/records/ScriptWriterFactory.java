package org.tamrielrebuilt.esm2yaml.esm.records;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

import org.tamrielrebuilt.esm2yaml.esm.CloseableRecordListener;
import org.tamrielrebuilt.esm2yaml.esm.EsmInputStream;
import org.tamrielrebuilt.esm2yaml.esm.RecordUtil;
import org.tamrielrebuilt.esm2yaml.esm.jackson.JsonLockWriter;
import org.tamrielrebuilt.esm2yaml.io.HexInputStream;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;

public class ScriptWriterFactory extends AbstractRecordListenerFactory {
	public ScriptWriterFactory() {
		super("SCPT");
	}

	@Override
	public CloseableRecordListener create(LockFileWriter writer, File directory, JsonFactory factory) {
		return new Listener(writer, new File(directory, "scripts"), factory);
	}

	private static class Listener implements CloseableRecordListener {
		private static final int HEADER = RecordUtil.getValue("SCHD");
		private static final int VARIABLES = RecordUtil.getValue("SCVR");
		private static final int BYTECODE = RecordUtil.getValue("SCDT");
		private static final int SOURCE = RecordUtil.getValue("SCTX");
		private final LockFileWriter lock;
		private final File baseDir;
		private final JsonFactory factory;
		private File directory;
		private int flags;
		private int unknown;
		private boolean open;
		private JsonGenerator generator;

		public Listener(LockFileWriter lock, File directory, JsonFactory factory) {
			this.lock = lock;
			this.baseDir = directory;
			this.factory = factory;
		}

		@Override
		public void onRecord(int type, int flags, int unknown) throws IOException {
			this.flags = flags;
			this.unknown = unknown;
			open = true;
		}

		@Override
		public void onSubrecord(int type, EsmInputStream input) throws IOException {
			if(type == HEADER) {
				String id = input.readName();
				int shorts = input.readLEInt();
				int longs = input.readLEInt();
				int floats = input.readLEInt();
				int data = input.readLEInt();
				int table = input.readLEInt();
				directory = new File(baseDir, id);
				directory.mkdirs();
				lock.writeRecord(directory);
				generator = factory.createGenerator(new FileOutputStream(new File(directory, ".metadata")));
				generator.writeStartObject();
				generator.writeStringField("id", id);
				JsonLockWriter.writeRecord(generator, flags, unknown);
				open = false;
				generator.writeNumberField("shorts", shorts);
				generator.writeNumberField("longs", longs);
				generator.writeNumberField("floats", floats);
				generator.writeNumberField("data", data);
				generator.writeNumberField("table", table);
			} else if(type == VARIABLES) {
				try(FileOutputStream output = new FileOutputStream(new File(directory, "script.var"))) {
					input.transferTo(output);
				}
			} else if(type == BYTECODE) {
				try(FileWriter writer = new FileWriter(new File(directory, "script.hex"), StandardCharsets.UTF_8)) {
					try(Reader reader = new InputStreamReader(new HexInputStream(input), StandardCharsets.US_ASCII)) {
						reader.transferTo(writer);
					}
				}
			} else if(type == SOURCE) {
				try(FileWriter writer = new FileWriter(new File(directory, "script.src"), StandardCharsets.UTF_8)) {
					try(Reader reader = input.asReader()) {
						reader.transferTo(writer);
					}
				}
			} else if(type == RecordUtil.DELETED) {
				generator.writeBooleanField("deleted", true);
			} else {
				generator.writeStringField(RecordUtil.toString(type), input.readAll());
			}
		}

		@Override
		public void onRecordEnd() throws IOException {
			if(generator != null) {
				generator.writeEndObject();
				generator.close();
				generator = null;
				directory = null;
			}
			if(open) {
				open = false;
				throw new IllegalStateException("Script record without subrecords");
			}
		}

		@Override
		public void close() throws IOException {
			onRecordEnd();
		}
	}
}
