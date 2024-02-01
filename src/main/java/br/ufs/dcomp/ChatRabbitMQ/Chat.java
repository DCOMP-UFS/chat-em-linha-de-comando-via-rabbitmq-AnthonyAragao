package br.ufs.dcomp.ChatRabbitMQ;
import com.rabbitmq.client.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Chat {

    private static String currentUser = "";  // Armazenar o nome do usuário atual
    private static String currentRecipient = "";  // Armazenar o destinatário atual
    private static String currentExchange = "";  // Armazenar o exchange
    private static String currentGroup = "";  
    private static boolean sendToGroup = false;  // Indica se a mensagem deve ser enviada para o grupo
    private static Channel channel;

    public static void main(String[] argv) throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("54.235.34.156"); 
        factory.setUsername("admin"); 
        factory.setPassword("password"); 
        factory.setVirtualHost("/");
        Connection connection = factory.newConnection();
        channel = connection.createChannel();

        // Pedir o nome do usuário
        System.out.print("User: ");
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        currentUser = reader.readLine();

        // Criar a fila do usuário no RabbitMQ
        String queueName = createQueue(currentUser, channel);

        // Configurar o consumidor de mensagens para a fila do usuário
        channel.basicConsume(queueName, true, createMessageConsumer(channel));

        // Exibir prompt inicial
        printPrompt();

        // Loop para lidar com entradas do usuário
        while (true) {
            String userInput = reader.readLine();
            if (userInput.equals("")) {
                // Exibir prompt novamente
                printPrompt();
            } else if (userInput.startsWith("@")) {
                // Mudar para outro usuário
                currentRecipient = userInput.substring(1);
                printRecipientPrompt();
                sendToGroup = false;
                
            } else if (userInput.startsWith("!")) {
                tratarComando(userInput, channel, queueName);
                printRecipientPrompt();
                
            } else if (userInput.startsWith("#")) {
                currentGroup = userInput.substring(1);
                printGroupPrompt();
                sendToGroup = true;
                
            } else {
                if(sendToGroup){
                    // Enviar mensagem para o grupo
                    String message = "(" + getCurrentTimestamp() + ") " + currentUser + " diz: " + userInput;
                    channel.basicPublish("amigos", "", null, message.getBytes("UTF-8"));
                    printGroupPrompt();
                }else{
                    // Enviar mensagem para a fila
                    String message = "(" + getCurrentTimestamp() + ") " + currentUser + " diz: " + userInput;
                    channel.basicPublish("", currentRecipient, null, message.getBytes("UTF-8"));
                    printRecipientPrompt();
                }
              
                
            }
        }
    }
    
    
    private static void tratarComando(String comando, Channel channel, String queueName){
        String arrayComando[] = comando.split(" ");
        String chamada = arrayComando[0].substring(1);
        
        // !addGroup amigos 
        if (chamada.equals("addGroup")) {
            
            try {
                String nomeDaFila = arrayComando[1];
                
                channel.exchangeDeclare(nomeDaFila, "fanout"); // Crio o grupo para enviar a mensagem para todas as filas
                channel.queueBind(queueName, nomeDaFila, ""); //  para não criar um grupo com nenhum particante, Ao criar o grupo, faço o bind com a fila atual
                
            } catch (IOException e) {
                e.printStackTrace();
            }
            
        } else if(chamada.equals("delFromGroup")){
            
            try {
                channel.queueUnbind(arrayComando[1], arrayComando[2], ""); // Corrigido para queueUnbind
            } catch (IOException e) {
                e.printStackTrace();
            }
            
            
        } else if(chamada.equals("removeGroup")){
            
            try {
                channel.exchangeDelete(arrayComando[1]); // Deleta o exchange
            } catch (IOException e) {
                e.printStackTrace();
            }
            
        
        } else if(chamada.equals("addUser")){
            try {
                channel.queueBind(arrayComando[1], arrayComando[2], "");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        
        
    };
    

    private static String createQueue(String user, Channel channel) throws IOException {
        String queueName = user;
        channel.queueDeclare(queueName, false, false, false, null);
        return queueName;
    }

    private static void printPrompt() {
        System.out.print(">> ");
    }

    private static void printRecipientPrompt() {
        System.out.print("@" + currentRecipient + ">> ");
    }
    
    private static void printGroupPrompt() {
        System.out.print("#" + currentGroup + ">> ");
    }

    private static String getCurrentTimestamp() {
        // Obter o timestamp atual
        LocalDateTime currentDateTime = LocalDateTime.now();

        // Formatar o timestamp
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");
        return formatter.format(currentDateTime);
    }

    private static DefaultConsumer createMessageConsumer(Channel channel) {
        return new DefaultConsumer(channel) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                String message = new String(body, "UTF-8");
                System.out.println(message);
                printRecipientPrompt();
            }
        };
    }
}