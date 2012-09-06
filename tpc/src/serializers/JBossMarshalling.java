package serializers;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Array;

import org.jboss.marshalling.ByteInput;
import org.jboss.marshalling.ByteOutput;
import org.jboss.marshalling.Marshaller;
import org.jboss.marshalling.MarshallerFactory;
import org.jboss.marshalling.Marshalling;
import org.jboss.marshalling.MarshallingConfiguration;
import org.jboss.marshalling.Unmarshaller;
import org.jboss.marshalling.reflect.SunReflectiveCreator;
import org.jboss.marshalling.river.RiverMarshallerFactory;
import org.jboss.marshalling.serial.SerialMarshallerFactory;

import data.media.MediaContent;

public class JBossMarshalling {

	public static void register(final TestGroups groups) {
		MarshallerFactory riverFactory = new RiverMarshallerFactory();

		groups.media.add(
			JavaBuiltIn.mediaTransformer,
			new MarshallingSerializer<MediaContent>(
				MediaContent.class,
				"jboss-marshalling-river",
				riverFactory,
				false,
				false
			)
		);
		groups.media.add(
			JavaBuiltIn.mediaTransformer,
			new MarshallingSerializer<MediaContent>(
				MediaContent.class,
				"jboss-marshalling-river-manual",
				riverFactory,
				false,
				true
			)
		);
		groups.media.add(
			JavaBuiltIn.mediaTransformer,
			new MarshallingSerializer<MediaContent>(
				MediaContent.class,
				"jboss-marshalling-river-ct",
				riverFactory,
				true,
				false
			)
		);
		groups.media.add(
			JavaBuiltIn.mediaTransformer,
			new MarshallingSerializer<MediaContent>(
				MediaContent.class,
				"jboss-marshalling-river-ct-manual",
				riverFactory,
				true,
				true
			)
		);
		groups.media.add(
			JavaBuiltIn.mediaTransformer,
			new MarshallingSerializer<MediaContent>(
				MediaContent.class,
				"jboss-marshalling-serial",
				new SerialMarshallerFactory(),
				false,
				false
			)
		);
	}

	private static final class MarshallingSerializer<T> extends Serializer<T> {

	    private final Class<T> clz;

	    private final Marshaller marshaller;

	    private final Unmarshaller unmarshaller;

	    private final String name;

	    private final ByteArrayInput input = new ByteArrayInput();

	    private final ByteArrayOutput output = new ByteArrayOutput();

	    public MarshallingSerializer(
    		final Class<T> clz,
    		final String name,
    		final MarshallerFactory marshallerFactory,
    		final boolean useCustomClassTable,
    		final boolean useExternalizers
		) {
	    	this.clz = clz;
	    	this.name = name;

	    	MarshallingConfiguration cfg = new MarshallingConfiguration();
	    	cfg.setBufferSize(Serializer.BUFFER_SIZE);
	    	cfg.setExternalizerCreator(new SunReflectiveCreator());

	    	if (useCustomClassTable) {
	    		cfg.setClassTable(new JBossMarshallingCEF.CustomClassTable());
	    	}

	    	if (useExternalizers) {
	    		cfg.setClassExternalizerFactory(new JBossMarshallingCEF());
	    	}

	    	try {
				marshaller = marshallerFactory.createMarshaller(cfg);
				unmarshaller = marshallerFactory.createUnmarshaller(cfg);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
	    }

        @Override
		public String getName() {
        	return name;
        }

        @Override
		public T deserialize(final byte[] array) throws Exception {
        	input.setBuffer(array);
        	unmarshaller.start(input);
			T val = unmarshaller.readObject(clz);
        	unmarshaller.finish();
	        return val;
	    }

	    @Override
		public byte[] serialize(final T data) throws IOException {
	    	marshaller.start(output);
	    	marshaller.writeObject(data);
	    	marshaller.finish();
	        return output.toByteArray();
	    }

        @Override
        public final void serializeItems(final T[] items, final OutputStream os)
    		throws Exception {
        	marshaller.start(Marshalling.createByteOutput(os));
            for (Object item : items) {
            	marshaller.writeObject(item);
            }
            marshaller.finish();
        }

		@Override
        public T[] deserializeItems(final InputStream in, final int numOfItems)
    		throws Exception {
        	unmarshaller.start(Marshalling.createByteInput(in));

			@SuppressWarnings("unchecked")
			T[] result = (T[]) Array.newInstance(clz, numOfItems);
            for (int i = 0; i < numOfItems; ++i) {
                result[i] = unmarshaller.readObject(clz);
            }
            unmarshaller.finish();

            return result;
        }

        private static final class ByteArrayInput implements ByteInput {

        	private byte[] buffer;

        	private int position;

        	public void setBuffer(final byte[] buffer) {
        		this.buffer = buffer;
        		position = 0;
        	}

			@Override
			public void close() throws IOException {
				buffer = null;
				position = -1;
			}

			@Override
			public int read() throws IOException {
				if (position >= buffer.length) {
					return -1;
				}

				return buffer[position++];
			}

			@Override
			public int read(final byte[] b) throws IOException {
				return read(b, 0, b.length);
			}

			@Override
			public int read(final byte[] b, final int off, final int len)
				throws IOException {
				if (position >= buffer.length) {
					return -1;
				}

				int n = len;
				if (n > buffer.length - position) {
					n = buffer.length - position;
				}

				System.arraycopy(buffer, position, b, off, n);
				position += n;

				return n;
			}

			@Override
			public int available() throws IOException {
				return buffer.length - position;
			}

			@Override
			public long skip(final long n) throws IOException {
				throw new IOException("Unsupported operation");
			}
        }

        private static final class ByteArrayOutput implements ByteOutput {

        	private byte[] buffer = new byte[Serializer.BUFFER_SIZE];

        	private int position;

			@Override
			public void close() throws IOException {
				position = 0;
				buffer = null;
			}

			@Override
			public void flush() throws IOException {
			}

			@Override
			public void write(final int b) throws IOException {
				throw new IOException("Unsupported operation");
			}

			@Override
			public void write(final byte[] b) throws IOException {
				write(b, 0, b.length);
			}

			@Override
			public void write(final byte[] b, final int off, final int len)
				throws IOException {
				if (buffer.length - position < len) {
					byte[] newBuffer = new byte[2 * buffer.length];
					System.arraycopy(buffer, 0, newBuffer, 0, position);
					buffer = newBuffer;
				}

				System.arraycopy(b, off, buffer, position, len);
				position += len;
			}

			public byte[] toByteArray() {
				byte[] result = new byte[position];
				System.arraycopy(buffer, 0, result, 0, position);
				position = 0;
				return result;
			}
        }
	}
}
