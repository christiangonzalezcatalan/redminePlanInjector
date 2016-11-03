package redminePlanInjector

import com.budjb.rabbitmq.consumer.MessageContext

class InjectorConsumer {

    static rabbitConfig = [
        'queue': 'testGemsBBPlan'
    ]

    def injectorService

    /**
     * Handle an incoming RabbitMQ message.
     *
     * @param body    The converted body of the incoming message.
     * @param context Properties of the incoming message.
     * @return
     */
    def handleMessage(def body, MessageContext messageContext) {
        injectorService.injectProcess()
    }
}
