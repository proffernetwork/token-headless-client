package com.bakkenbaeck.token.headless;

import com.bakkenbaeck.token.headless.rpc.HeadlessRPC;
import com.bakkenbaeck.token.headless.rpc.entities.HeadlessRPCRequest;
import com.bakkenbaeck.token.headless.signal.AttachmentInvalidException;
import com.bakkenbaeck.token.headless.signal.Manager;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.whispersystems.signalservice.api.push.exceptions.EncapsulatedExceptions;
import redis.clients.jedis.JedisPubSub;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;

// for converting base 64 image to image file
import javax.xml.bind.DatatypeConverter;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

class RedisSubscriber extends JedisPubSub {
   // private static Logger logger = Logger.getLogger(RedisSubscriber.class);
    private Manager manager;
    private ObjectMapper mapper;
    private HeadlessRPC rpc;

    public RedisSubscriber(HeadlessRPC rpc, Manager manager) {
        this.rpc = rpc;
        this.manager = manager;
        mapper = new ObjectMapper();
    }

    @Override
    public void onMessage(String channel, String message) {
        //System.out.println("Redis message received on channel "+channel+": "+message);
        if (channel.equals(this.manager.getUsername())) {
            try {
                SignalWrappedSOFA wrapped = mapper.readValue(message, SignalWrappedSOFA.class);
                if (!wrapped.getSender().equals(manager.getUsername())) {
                    //System.out.println("Ignoring: "+wrapped.getSender()+" is not "+manager.getUsername());
                    return;
                }

                ObjectMapper mapper = new ObjectMapper();
                String s = wrapped.getSofa().split("SOFA::\\w+:")[1];
                JsonNode sofa = mapper.readTree(s);
                ArrayList<String> attachments = new ArrayList<String>();

                if (sofa.has("attachments")) {
                    for (int i = 0; i < sofa.get("attachments").size(); i++) {
                        JsonNode urlNode = sofa.get("attachments").get(i).get("url");

                        if (urlNode != null) {
                            String url = urlNode.asText();

                            // This is a base64 image
                            if (url.indexOf(";base64,") >= 0){
                              String base64data = url.split(",")[1];

                              // create a java Image from the data
                              byte[] imageBytes = DatatypeConverter.parseBase64Binary(base64data);
                              BufferedImage img = ImageIO.read(new ByteArrayInputStream(imageBytes));

                              // save the file to 'attachments/' + filename created using random int
                              int randomNum = (int)(Math.random() * 1000000);
                              String randomFileName = ""+randomNum+".png";
                              String filePath = "attachments/" + randomFileName;

                              try {
                                File outputfile = new File(filePath);
                                ImageIO.write(img, "png", outputfile);

                                attachments.add(filePath);
                                System.out.println("Successfully saved the image to " + filePath + " and will now send in message.");

                              }  catch (IOException e) {
                                  System.out.println(e);
                                  System.out.println("Could not save " + filePath + " but failed. Will not send this attachment.");
                              }
                            }

                            // This is a regular image, NOT a base64 image
                            else {
                              String filePath = "attachments/" + url;
                              Boolean exists = new File(filePath).exists();
                              if (exists) {
                                  attachments.add(filePath);
                              } else {
                                  System.out.println("Attachment " + filePath + " does not exist");
                              }
                            }
                        } else {
                            System.out.println("Attachment is missing the 'url' property");
                        }
                    }
                }

                //System.out.println(wrapped.getSofa());
                try {
                    manager.sendMessage(wrapped.getSofa(), attachments, wrapped.getRecipient());
                } catch (EncapsulatedExceptions encapsulatedExceptions) {
                    encapsulatedExceptions.printStackTrace();
                } catch (AttachmentInvalidException e) {
                    e.printStackTrace();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (channel.equals(this.manager.getUsername()+"_rpc_request")) {
            try {
                HeadlessRPCRequest request = mapper.readValue(message, HeadlessRPCRequest.class);
                rpc.handleRequest(request);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    @Override
    public void onPMessage(String pattern, String channel, String message) {

    }

    @Override
    public void onSubscribe(String channel, int subscribedChannels) {
          System.out.println("onSubcribe "+channel);
    }

    @Override
    public void onUnsubscribe(String channel, int subscribedChannels) {
        System.out.println("onUnSubcribe "+channel);
    }

    @Override
    public void onPUnsubscribe(String pattern, int subscribedChannels) {

    }

    @Override
    public void onPSubscribe(String pattern, int subscribedChannels) {

    }
}
