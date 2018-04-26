package es.um.redes.nanoGames.broker;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

/**
 * Cliente SNMP sin dependencias con otras clases y con funciones de consulta
 * espec�ficas. En la actual versi�n s�lo soporta una funci�n de consulta sobre
 * el UPTIME del host.
 */
public class BrokerClient {
	private static final int PACKET_MAX_SIZE = 484;
	private static final int DEFAULT_PORT = 161;
	private static final String OID_UPTIME = "1.3.6.1.2.1.1.3.0";
	public final int MAX_MSG_SIZE_BYTES = 1024;
	public static final String serverName = "localhost";

	private DatagramSocket socket; // socket UDP
	private InetSocketAddress agentAddress; // direcci�n del agente SNMP
	private InetSocketAddress addr;

	/**
	 * Constructor usando par�metros por defecto
	 */
	public BrokerClient(String agentAddress) {
		// Registrar dirección del servidor
		// Crear socket de cliente
		// get a datagram socket
		try {
			socket = new DatagramSocket();
			addr = new InetSocketAddress(InetAddress.getByName(agentAddress),
					DEFAULT_PORT);
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (SocketException e) {
			e.printStackTrace();
		}
	}

	private byte[] buildRequest() throws IOException {
		// mensaje GetRequest
		ByteArrayOutputStream request = new ByteArrayOutputStream();
		request.write(new byte[] { 0x30, 0x26 }); // Message (SEQUENCE)
		request.write(new byte[] { 0x02, 0x01, 0x00 }); // Version
		request.write(new byte[] { 0x04, 0x06 }); // Community
		request.write("public".getBytes());
		request.write(new byte[] { (byte) 0xa0, 0x19 }); // GetRequest
		request.write(new byte[] { (byte) 0x02, 0x01, 0x00 }); // RequestId
		request.write(new byte[] { (byte) 0x02, 0x01, 0x00 }); // ErrorStatus
		request.write(new byte[] { (byte) 0x02, 0x01, 0x00 }); // ErrorIndex
		request.write(new byte[] { (byte) 0x30, 0x0e }); // Bindings (SEQUENCE)
		request.write(new byte[] { (byte) 0x30, 0x0c }); // Bindings Child
															// (SEQUENCE)
		request.write(new byte[] { (byte) 0x06 }); // OID
		byte[] oidArray = encodeOID(OID_UPTIME);
		request.write((byte) oidArray.length);
		request.write(oidArray);
		request.write(new byte[] { (byte) 0x05, 0x00 }); // Value (NULL)

		return request.toByteArray();

	}

	private long getTimeTicks(byte[] data) {
		ByteArrayInputStream response = new ByteArrayInputStream(data);

		// recuperamos timeTicks a partir de la respuesta
		int ch;
		while ((ch = response.read()) != -1) {
			if (ch == 0x43) { // TimeTicks
				int len = response.read();
				byte[] value = new byte[len];
				response.read(value, 0, len);
				return new BigInteger(value).longValue();
			}
		}
		return 0;
	}

	/**
	 * Env�a un solicitud GET al agente para el objeto UPTIME
	 * 
	 * @return long
	 * @throws IOException
	 */
	public long getToken() throws IOException {

		// Construir solicitud
		// Enviar solicitud
		// Recibir respuesta
		// Extraer TimeTicks (Token)
		// Devolver token

		// allocate buffer and prepare message to be sent
		// byte[] req = new byte [MAX_MSG_SIZE_BYTES];
		DatagramPacket packet = null;
		long timeStamp = 0;
		byte[] req = null;
		int timeoutNum = 0;
		boolean received = false;

		// send request
		try {
			req = buildRequest();
		} catch (IOException e) {
			e.printStackTrace();
		}
		byte[] response = new byte[MAX_MSG_SIZE_BYTES];

		while (!received) {
			try {
				packet = new DatagramPacket(req, req.length, addr);
				socket.send(packet);
				// receive response
				packet = new DatagramPacket(response, response.length);
				socket.setSoTimeout(1000);
				socket.receive(packet);
				received = true;
			} catch (SocketTimeoutException e) {
				System.err.println("TimeOutException\t TIMEOUT " + ++timeoutNum);
			} catch (SocketException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		ByteArrayInputStream responseStream = new ByteArrayInputStream(
				packet.getData());
		int x;
		while ((x = responseStream.read()) != -1)
			System.out.print(x + " ");
		System.out.println();
		timeStamp = getTimeTicks(response);
		socket.close();

		return timeStamp;
	}

	/**
	 * Codifica un OID según la especifación SNMP Nota: sólo soporta OIDs con
	 * números de uno o dos dígitos
	 * 
	 * @param oid
	 * @return
	 */
	private byte[] encodeOID(String oid) {
		// parsea OID
		String digits[] = oid.split("\\.");
		byte[] value = new byte[digits.length];
		for (int i = 0; i < digits.length; i++)
			value[i] = (byte) Byte.parseByte(digits[i]);

		// codifica OID
		byte[] ret = new byte[value.length - 1];
		byte x = value[0];
		byte y = value.length <= 1 ? 0 : value[1];
		for (int i = 1; i < value.length; i++) {
			ret[i - 1] = (byte) ((i != 1) ? value[i] : x * 40 + y);
		}
		return ret;
	}

	public void close() {
		socket.close();
	}
}
