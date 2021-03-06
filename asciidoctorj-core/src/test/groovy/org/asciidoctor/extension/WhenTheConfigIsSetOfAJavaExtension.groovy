package org.asciidoctor.extension

import org.asciidoctor.Asciidoctor
import org.asciidoctor.OptionsBuilder
import org.asciidoctor.internal.AsciidoctorCoreException
import spock.lang.Specification

class WhenTheConfigIsSetOfAJavaExtension extends Specification {

    String document = '''[modify]
Parsing will crash when processing this block
'''

    def 'setConfig should throw no Exception as long as the processor is not used by Asciidoctor'() {

        given:
        BlockProcessor blockProcessor = new ConfigModifyingBlockProcessor()
        Map<String, Object> config = [:]
        config[BlockProcessor.CONTEXTS] = [BlockProcessor.CONTEXT_PARAGRAPH]
        config[Processor.CONTENT_MODEL] = Processor.CONTENT_MODEL_SIMPLE
        blockProcessor.config = config

        expect:
        blockProcessor.config[Processor.CONTENT_MODEL] == Processor.CONTENT_MODEL_SIMPLE
    }

    def 'setConfig should throw an IllegalStateException in Processor_process when a processor instance is registered'() {

        given:
        Asciidoctor asciidoctor = Asciidoctor.Factory.create()

        BlockProcessor blockProcessor = new ConfigModifyingBlockProcessor()
        Map<String, Object> config = [:]
        config[BlockProcessor.CONTEXTS] = [BlockProcessor.CONTEXT_PARAGRAPH]
        config[Processor.CONTENT_MODEL] = Processor.CONTENT_MODEL_SIMPLE
        blockProcessor.config = config

        asciidoctor.javaExtensionRegistry().block(blockProcessor)

        when:
        asciidoctor.render(document, OptionsBuilder.options().toFile(false))

        then:
        Exception e = thrown()
        e instanceof AsciidoctorCoreException || e instanceof IllegalStateException
    }

    def 'setConfig should throw an IllegalStateException in Processor_process when a processor class is registered'() {

        given:
        Asciidoctor asciidoctor = Asciidoctor.Factory.create()

        asciidoctor.javaExtensionRegistry().block('modify', ConfigModifyingBlockProcessor)

        when:
        asciidoctor.render(document, OptionsBuilder.options().toFile(false))

        then:
        Exception e = thrown()
        e instanceof AsciidoctorCoreException || e instanceof IllegalStateException
    }

}
