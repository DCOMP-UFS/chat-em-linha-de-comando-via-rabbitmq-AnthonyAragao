package br.ufs.dcomp.ChatRabbitMQ;

import com.rabbitmq.client.*;

import java.io.IOException;

import java.util.Scanner;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import java.io.*;
import com.google.protobuf.util.JsonFormat;
import com.google.protobuf.ByteString;

import java.nio.file.*;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import com.google.gson.*;

public class Chat {
  
  public static String usuario="";
  
  public static void main(String[] argv) throws Exception {
    ConnectionFactory factory = new ConnectionFactory();
    factory.setHost("54.83.96.48"); 
    factory.setUsername("admin"); 
    factory.setPassword("password"); 
    factory.setVirtualHost("/");
    Connection connection = factory.newConnection();
    Channel channel = connection.createChannel();
    Channel channelFile = connection.createChannel();
    Scanner ler = new Scanner(System.in);

    System.out.print("User: ");
    String QUEUE_NAME;
    QUEUE_NAME = ler.nextLine();
    
    channel.queueDeclare(QUEUE_NAME, false,   false,     false,       null);
    channelFile.queueDeclare(QUEUE_NAME+"Files", false,   false,     false,       null);

    
    Consumer consumer = new DefaultConsumer(channel) {
        public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body)
          throws IOException {
  
          //String messageReceived = new String(body, "UTF-8");
          System.out.println("");
          
          MensagemProto.Mensagem msg = MensagemProto.Mensagem.parseFrom(body);
          MensagemProto.Conteudo conteudo = msg.getConteudo();
          
          String emissor = msg.getEmissor();
          String date = msg.getData();
          String hora = msg.getHora();
          String group = msg.getGrupo();
          String stringConteudo = conteudo.getCorpo().toStringUtf8();
          
          String msgFrom;
          if(group.equals("")){
            msgFrom = emissor;
          }else{
            msgFrom = emissor+"#"+group;
          }
          
          String messageReceived = "("+date+" às "+hora+") "+msgFrom+" diz: "+ stringConteudo;
          System.out.println(messageReceived);
          System.out.print(usuario+">> ");
        }
      };
      
      Consumer consumerFile = new DefaultConsumer(channel) {
        public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body)
          throws IOException {
  
          //String messageReceived = new String(body, "UTF-8");
          System.out.println("");
          
          MensagemProto.Mensagem msg = MensagemProto.Mensagem.parseFrom(body);
          MensagemProto.Conteudo conteudo = msg.getConteudo();
          
          String emissor = msg.getEmissor();
          String date = msg.getData();
          String hora = msg.getHora();
          String group = msg.getGrupo();
          String nome = conteudo.getNome();
          String tipo = conteudo.getTipo();
          byte[] stringConteudo = conteudo.getCorpo().toByteArray();
          
          File dir = new File("home/downloads/");
          dir.mkdirs();
          File file = new File(dir, nome);
          FileOutputStream saida = new FileOutputStream(file);
          BufferedOutputStream out = new BufferedOutputStream(saida);
          out.write(stringConteudo);
          out.flush();
          out.close();
          
          String messageReceived = "("+date+" às "+hora+") Arquivo "+nome+" recebido de @"+emissor+"!";
          System.out.println(messageReceived);
          System.out.print(usuario+">> ");
        }
      };
                        //(queue-name, autoAck, consumer);    
    channel.basicConsume(QUEUE_NAME, true,    consumer);
    channelFile.basicConsume(QUEUE_NAME+"Files", true,    consumerFile);
    
    String leitura,message;
    int t=0;
    
    System.out.print(usuario+">> ");
    while(true){
      leitura = ler.nextLine();
      if(leitura.equals("") || leitura.isEmpty()){
        System.out.print(usuario+">> ");
      }
      else if(leitura.startsWith("@")){
        usuario = leitura;
        t = usuario.length();
        System.out.print(usuario+">> ");
      }
      else if(leitura.startsWith("#")){
        usuario = leitura;
        t = usuario.length();
        System.out.print(usuario+">> ");
      }
      else if(leitura.startsWith("!addGroup")){
        String exchangeName = leitura.replace("!addGroup ","");
        channel.exchangeDeclare(exchangeName, "fanout");
        channel.exchangeDeclare(exchangeName+"Files", "fanout");
        
        channel.queueBind(QUEUE_NAME, exchangeName, "");
        channel.queueBind(QUEUE_NAME+"Files", exchangeName+"Files", "");
        System.out.print(usuario+">> ");
      }
      else if(leitura.startsWith("!addUser")){
        String user_group = leitura.replace("!addUser ","");
        String user = user_group.substring(0,user_group.indexOf(" "));
        String exchangeName = user_group.substring(user_group.indexOf(" ")+1);
        channel.queueBind(user, exchangeName, "");
        channel.queueBind(user+"Files", exchangeName+"Files", "");
        System.out.print(usuario+">> ");
      }
      else if(leitura.startsWith("!delFromGroup")){
        String user_group = leitura.replace("!delFromGroup ","");
        String user = user_group.substring(0,user_group.indexOf(" "));
        String exchangeName = user_group.substring(user_group.indexOf(" ")+1);
        channel.queueUnbind(user, exchangeName, "");
        channel.queueUnbind(user+"Files", exchangeName+"Files", "");
        System.out.print(usuario+">> ");
      }
      else if(leitura.startsWith("!removeGroup")){
        String exchangeName = leitura.replace("!removeGroup ","");
        channel.exchangeDelete(exchangeName);
        channel.exchangeDelete(exchangeName+"Files");
        System.out.print(usuario+">> ");
      }
      else if(leitura.startsWith("!upload") && !usuario.equals("")){
        String path = leitura.replace("!upload /","");
        String nome = leitura.substring(leitura.lastIndexOf("/")+1);
        System.out.println("Enviando "+path+" para "+usuario.substring(0,t)+".");
        System.out.print(usuario+">> ");
        LocalDate date = LocalDate.now();
        DateTimeFormatter myFormatObj = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        LocalTime time = LocalTime.now();
        
        Path source = Paths.get(path);
        String tipoMime = Files.probeContentType(source);
        
        File file = new File(path);
        int len = (int)file.length();
        byte[] arquivo = new byte[len];
        FileInputStream input  = new FileInputStream(file);
        input.read(arquivo);
        input.close();
        
        MensagemProto.Conteudo.Builder bConteudo = MensagemProto.Conteudo.newBuilder();
        bConteudo.setTipo(tipoMime);
        bConteudo.setCorpo(ByteString.copyFrom(arquivo));
        bConteudo.setNome(nome);
        
        MensagemProto.Mensagem.Builder bMensagem = MensagemProto.Mensagem.newBuilder();
        bMensagem.setEmissor(QUEUE_NAME);
        bMensagem.setData(date.format(myFormatObj));
        bMensagem.setHora(time.toString().substring(0,5));
        bMensagem.setConteudo(bConteudo);
        
        if(usuario.startsWith("#")){
          bMensagem.setGrupo(usuario.substring(1,t));
          MensagemProto.Mensagem msg = bMensagem.build();
          byte[] buffer = msg.toByteArray();
          
          channel.basicPublish(usuario.substring(1,t)+"Files", "", null,  buffer); //Enviar msg grupo
        }else{
          bMensagem.setGrupo("");
          MensagemProto.Mensagem msg = bMensagem.build();
          byte[] buffer = msg.toByteArray();
        
          channel.basicPublish("", usuario.substring(1,t)+"Files", null,  buffer); //Enviar msg 
        }
        
        System.out.println("");
        System.out.println("Arquivo "+path+" foi enviado para "+usuario.substring(0,t)+"!");
        System.out.print(usuario+">> ");
      }
      else if(leitura.startsWith("!listUsers")){
        String grupo = leitura.replace("!listUsers ","");
        String username = "admin";
        String password = "password";
        String usernameAndPassword = username + ":" + password;
        String authorizationHeaderName = "Authorization";
        String authorizationHeaderValue = "Basic " + java.util.Base64.getEncoder().encodeToString( usernameAndPassword.getBytes() );
        // Perform a request
        String restResource = "http://RabbitMQ-SD-T02-LB-a88c46d8c9af788f.elb.us-east-1.amazonaws.com";
        Client client = ClientBuilder.newClient();
        
        Response resposta = client.target( restResource )
        //.path("/api/exchanges/%2f/ufs/bindings/source") // lista todos os binds que tem o exchange "ufs" como source	
          .path("/api/exchanges/%2f/"+grupo+"/bindings/source")
          .request(MediaType.APPLICATION_JSON)
          .header( authorizationHeaderName, authorizationHeaderValue ) // The basic authentication header goes here
          .get();
          
        if (resposta.getStatus() == 200) {
          String json = resposta.readEntity(String.class);
  
          JsonArray saida = (new Gson()).fromJson(json, JsonArray.class);
            for (int i = 0; i < saida.size(); i++) {
              System.out.print(saida.get(i).getAsJsonObject().get("destination").getAsString());
              if (i != saida.size()-1)
                System.out.print(", ");
            }
            System.out.println("");
            System.out.print(usuario+">> ");
        } else {
          System.out.println(resposta.getStatus());
        }  
      }
      else if(leitura.startsWith("!listGroups")){
        String username = "admin";
        String password = "password";
        String usernameAndPassword = username + ":" + password;
        String authorizationHeaderName = "Authorization";
        String authorizationHeaderValue = "Basic " + java.util.Base64.getEncoder().encodeToString( usernameAndPassword.getBytes() );
        // Perform a request
        String restResource = "http://RabbitMQ-SD-T02-LB-a88c46d8c9af788f.elb.us-east-1.amazonaws.com";
        Client client = ClientBuilder.newClient();
        
        Response resposta = client.target( restResource )
        //.path("/api/exchanges/%2f/ufs/bindings/source") // lista todos os binds que tem o exchange "ufs" como source	
          .path("/api/queues/%2f/"+QUEUE_NAME+"/bindings")
          .request(MediaType.APPLICATION_JSON)
          .header( authorizationHeaderName, authorizationHeaderValue ) // The basic authentication header goes here
          .get();
          
        if (resposta.getStatus() == 200) {
          String json = resposta.readEntity(String.class);
  
          JsonArray saida = (new Gson()).fromJson(json, JsonArray.class);
            for (int i = 1; i < saida.size(); i++) {
              System.out.print(saida.get(i).getAsJsonObject().get("source").getAsString());
              if (i != saida.size()-1)
                System.out.print(", ");
            }
            System.out.println("");
            System.out.print(usuario+">> ");
        } else {
          System.out.println(resposta.getStatus());
        }  
      }
      else{
        if(usuario.equals("")){
          System.out.print(usuario+">> ");
        }
        else{
          LocalDate date = LocalDate.now();
          DateTimeFormatter myFormatObj = DateTimeFormatter.ofPattern("dd/MM/yyyy");
          LocalTime time = LocalTime.now();
          
          MensagemProto.Conteudo.Builder bConteudo = MensagemProto.Conteudo.newBuilder();
          bConteudo.setTipo("text/plain");
          bConteudo.setCorpo(ByteString.copyFromUtf8(leitura));
          bConteudo.setNome("");
          
          MensagemProto.Mensagem.Builder bMensagem = MensagemProto.Mensagem.newBuilder();
          bMensagem.setEmissor(QUEUE_NAME);
          bMensagem.setData(date.format(myFormatObj));
          bMensagem.setHora(time.toString().substring(0,5));
          bMensagem.setConteudo(bConteudo);
          
          if(usuario.startsWith("#")){
            bMensagem.setGrupo(usuario.substring(1,t));
            MensagemProto.Mensagem msg = bMensagem.build();
            byte[] buffer = msg.toByteArray();
            
            channel.basicPublish(usuario.substring(1,t), "", null,  buffer); //Enviar msg grupo
          }else{
            bMensagem.setGrupo("");
            MensagemProto.Mensagem msg = bMensagem.build();
            byte[] buffer = msg.toByteArray();
            
            channel.basicPublish("", usuario.substring(1,t), null,  buffer); //Enviar msg
          }
          
          System.out.print(usuario+">> ");
        }
    }
  }
}
}