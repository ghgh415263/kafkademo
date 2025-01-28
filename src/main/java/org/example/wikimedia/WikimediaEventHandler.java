package org.example.wikimedia;

import com.launchdarkly.eventsource.EventHandler;
import com.launchdarkly.eventsource.MessageEvent;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WikimediaEventHandler implements EventHandler {

    private final String topic;

    private final Producer<String,String> producer;

    private static final Logger log = LoggerFactory.getLogger(WikimediaEventHandler.class.getName());

    public WikimediaEventHandler(String topic, Producer<String, String> producer) {
        this.topic = topic;
        this.producer = producer;
    }

    @Override
    public void onOpen(){

    }

    @Override
    public void onClosed(){
        producer.close();
    }

    @Override
    public void onMessage(String event, MessageEvent messageEvent){
        log.info("message 받음 : {}", messageEvent.getData());
        producer.send(new ProducerRecord<>(topic, messageEvent.getData()));
    }

    @Override
    public void onComment(String comment) {

    }

    @Override
    public void onError(Throwable t) {

    }
}
