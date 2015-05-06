package org.asciidoctor.internal;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.asciidoctor.Asciidoctor;
import org.asciidoctor.Attributes;
import org.asciidoctor.DirectoryWalker;
import org.asciidoctor.Options;
import org.asciidoctor.OptionsBuilder;
import org.asciidoctor.ast.AbstractBlock;
import org.asciidoctor.ast.ContentPart;
import org.asciidoctor.ast.Document;
import org.asciidoctor.ast.DocumentHeader;
import org.asciidoctor.ast.DocumentRuby;
import org.asciidoctor.ast.StructuredDocument;
import org.asciidoctor.ast.Title;
import org.asciidoctor.converter.JavaConverterRegistry;
import org.asciidoctor.converter.internal.ConverterRegistryExecutor;
import org.asciidoctor.extension.JavaExtensionRegistry;
import org.asciidoctor.extension.RubyExtensionRegistry;
import org.asciidoctor.extension.internal.ExtensionRegistryExecutor;
import org.jruby.CompatVersion;
import org.jruby.Ruby;
import org.jruby.RubyHash;
import org.jruby.RubyInstanceConfig;
import org.jruby.RubyInstanceConfig.CompileMode;
import org.jruby.embed.ScriptingContainer;
import org.jruby.exceptions.RaiseException;
import org.jruby.javasupport.JavaEmbedUtils;

public class JRubyAsciidoctor implements Asciidoctor {

    static {

        final Logger logger = Logger.getLogger("Asciidoctor");
        final LoggerOutputStream out = new LoggerOutputStream(logger);
        System.setOut(new PrintStream(out));
        System.setErr(new PrintStream(out));

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                try { // can log or not something depending logger is already closed or not, best is to call it manually at the end of main if possible
                    out.flush();
                } catch (final IOException e) {
                    // no-op
                }
            }
        });

    }


    private static final Logger logger = Logger.getLogger(JRubyAsciidoctor.class.getName());

    private static final String GEM_PATH = "GEM_PATH";

    private static final int DEFAULT_MAX_LEVEL = 1;

    private AsciidoctorModule asciidoctorModule;
    protected RubyGemsPreloader rubyGemsPreloader;
    protected Ruby rubyRuntime;

    JRubyAsciidoctor(final AsciidoctorModule asciidoctorModule, final Ruby rubyRuntime) {
        super();
        this.asciidoctorModule = asciidoctorModule;
        this.rubyRuntime = rubyRuntime;
        this.rubyGemsPreloader = new RubyGemsPreloader(this.rubyRuntime);
    }

    public static Asciidoctor create() {
        Map<String, Object> env = new HashMap<String, Object>();
        // ideally, we want to clear GEM_PATH by default, but for backwards compatibility we play nice
        //env.put(GEM_PATH, null);
        Asciidoctor asciidoctor = createJRubyAsciidoctorInstance(env);
        registerExtensions(asciidoctor);
        registerConverters(asciidoctor);

        return asciidoctor;
    }

    public static Asciidoctor create(String gemPath) {
        Map<String, Object> env = new HashMap<String, Object>();
        // a null value will clear the GEM_PATH and GEM_HOME
        env.put(GEM_PATH, gemPath);

        Asciidoctor asciidoctor = createJRubyAsciidoctorInstance(env);
        registerExtensions(asciidoctor);
        registerConverters(asciidoctor);

        return asciidoctor;
    }

    public static Asciidoctor create(List<String> loadPaths) {
        Asciidoctor asciidoctor = createJRubyAsciidoctorInstance(loadPaths);
        registerExtensions(asciidoctor);
        registerConverters(asciidoctor);

        return asciidoctor;
    }

    public static Asciidoctor create(ClassLoader classloader) {
        Asciidoctor asciidoctor = createJRubyAsciidoctorInstance(classloader, null);
        registerExtensions(asciidoctor);

        return asciidoctor;
    }
    public static Asciidoctor create(ClassLoader classloader, String gemPath) {
        Asciidoctor asciidoctor = createJRubyAsciidoctorInstance(classloader, gemPath);
        registerExtensions(asciidoctor);
        registerConverters(asciidoctor);

        return asciidoctor;
    }

    private static void registerConverters(Asciidoctor asciidoctor) {
        new ConverterRegistryExecutor(asciidoctor).registerAllConverters();
    }

    private static void registerExtensions(Asciidoctor asciidoctor) {
        new ExtensionRegistryExecutor(asciidoctor).registerAllExtensions();
    }

    private static Asciidoctor createJRubyAsciidoctorInstance(List<String> loadPaths) {

        RubyInstanceConfig config = createOptimizedConfiguration();

        Ruby rubyRuntime = JavaEmbedUtils.initialize(loadPaths, config);
        JRubyRuntimeContext.set(rubyRuntime);

        JRubyAsciidoctorModuleFactory jRubyAsciidoctorModuleFactory = new JRubyAsciidoctorModuleFactory(rubyRuntime);

        AsciidoctorModule asciidoctorModule = jRubyAsciidoctorModuleFactory.createAsciidoctorModule();
        JRubyAsciidoctor jRubyAsciidoctor = new JRubyAsciidoctor(asciidoctorModule, rubyRuntime);

        return jRubyAsciidoctor;
    }

    private static Asciidoctor createJRubyAsciidoctorInstance(Map<String, Object> environmentVars) {

        RubyInstanceConfig config = createOptimizedConfiguration();
        injectEnvironmentVariables(config, environmentVars);

        Ruby rubyRuntime = JavaEmbedUtils.initialize(Collections.EMPTY_LIST, config);

        JRubyRuntimeContext.set(rubyRuntime);

        JRubyAsciidoctorModuleFactory jRubyAsciidoctorModuleFactory = new JRubyAsciidoctorModuleFactory(rubyRuntime);

        AsciidoctorModule asciidoctorModule = jRubyAsciidoctorModuleFactory.createAsciidoctorModule();

        JRubyAsciidoctor jRubyAsciidoctor = new JRubyAsciidoctor(asciidoctorModule, rubyRuntime);
        return jRubyAsciidoctor;
    }

    private static Asciidoctor createJRubyAsciidoctorInstance(ClassLoader classloader, String gemPath) {

        Map<String, Object> env = new HashMap<String, Object>();
        env.put(GEM_PATH, gemPath);

        ScriptingContainer container = new ScriptingContainer();
        injectEnvironmentVariables(container.getProvider().getRubyInstanceConfig(), env);
        container.setClassLoader(classloader);
        Ruby rubyRuntime = container.getProvider().getRuntime();

        JRubyRuntimeContext.set(rubyRuntime);

        JRubyAsciidoctorModuleFactory jRubyAsciidoctorModuleFactory = new JRubyAsciidoctorModuleFactory(rubyRuntime);

        AsciidoctorModule asciidoctorModule = jRubyAsciidoctorModuleFactory.createAsciidoctorModule();
        JRubyAsciidoctor jRubyAsciidoctor = new JRubyAsciidoctor(asciidoctorModule, rubyRuntime);

        return jRubyAsciidoctor;
    }

    static void injectEnvironmentVariables(final RubyInstanceConfig config, final Map<String, Object> environmentVars) {
        final EnvironmentInjector environmentInjector = new EnvironmentInjector(config);
        environmentInjector.inject(environmentVars);
    }

    private static RubyInstanceConfig createOptimizedConfiguration() {
        RubyInstanceConfig config = new RubyInstanceConfig();
        config.setCompatVersion(CompatVersion.RUBY2_0);
        config.setCompileMode(CompileMode.OFF);

        return config;
    }

    private DocumentHeader toDocumentHeader(DocumentRuby documentRuby) {
        Map<Object, Object> opts = new HashMap<Object, Object>();
        opts.put("partition", true);

        Document document = new Document(documentRuby, rubyRuntime);

        return DocumentHeader.createDocumentHeader((Title) document.doctitle(opts), documentRuby.title(),
                documentRuby.getAttributes());
    }

    private StructuredDocument toDocument(DocumentRuby documentRuby, Ruby rubyRuntime, int maxDeepLevel) {

        Document document = new Document(documentRuby, rubyRuntime);
        List<ContentPart> contentParts = getContents(document.blocks(), 1, maxDeepLevel);
        return StructuredDocument.createStructuredDocument(toDocumentHeader(documentRuby), contentParts);
    }

    private List<ContentPart> getContents(List<AbstractBlock> blocks, int level, int maxDeepLevel) {
        // finish getting childs if max structure level was riched
        if (level > maxDeepLevel) {
            return null;
        }
        // if document has only one child don't treat as actual contentpart
        // unless
        // it has no childs
        /*
         * if (blocks.size() == 1 && blocks.get(0).blocks().size() > 0) { return getContents(blocks.get(0).blocks(), 0,
         * maxDeepLevel); }
         */
        // add next level of contentParts
        List<ContentPart> parts = new ArrayList<ContentPart>();
        for (AbstractBlock block : blocks) {
            parts.add(getContentPartFromBlock(block, level, maxDeepLevel));
        }
        return parts;
    }

    private ContentPart getContentPartFromBlock(AbstractBlock child, int level, int maxDeepLevel) {
        Object content = child.content();
        String textContent;
        if (content instanceof String) {
            textContent = (String) content;
        } else {
            textContent = child.convert();
        }
        ContentPart contentPart = ContentPart.createContentPart(child.id(), level, child.context(), child.title(),
                child.style(), child.role(), child.getAttributes(), textContent);
        contentPart.setParts(getContents(child.blocks(), level + 1, maxDeepLevel));
        return contentPart;
    }

    @SuppressWarnings("unchecked")
    @Override
    public StructuredDocument readDocumentStructure(File filename, Map<String, Object> options) {

        this.rubyGemsPreloader.preloadRequiredLibraries(options);

        RubyHash rubyHash = RubyHashUtil.convertMapToRubyHashWithSymbols(rubyRuntime, options);
        DocumentRuby documentRuby = this.asciidoctorModule.load_file(filename.getAbsolutePath(), rubyHash);
        int maxDeepLevel = options.containsKey(STRUCTURE_MAX_LEVEL) ? (Integer) (options.get(STRUCTURE_MAX_LEVEL))
                : DEFAULT_MAX_LEVEL;
        return toDocument(documentRuby, rubyRuntime, maxDeepLevel);
    }

    @SuppressWarnings("unchecked")
    @Override
    public StructuredDocument readDocumentStructure(String content, Map<String, Object> options) {

        this.rubyGemsPreloader.preloadRequiredLibraries(options);

        RubyHash rubyHash = RubyHashUtil.convertMapToRubyHashWithSymbols(rubyRuntime, options);

        DocumentRuby documentRuby = this.asciidoctorModule.load(content, rubyHash);
        int maxDeepLevel = options.containsKey(STRUCTURE_MAX_LEVEL) ? (Integer) (options.get(STRUCTURE_MAX_LEVEL))
                : DEFAULT_MAX_LEVEL;
        return toDocument(documentRuby, rubyRuntime, maxDeepLevel);
    }

    @Override
    public StructuredDocument readDocumentStructure(Reader contentReader, Map<String, Object> options) {
        String content = IOUtils.readFull(contentReader);
        return readDocumentStructure(content, options);
    }

    @SuppressWarnings("unchecked")
    @Override
    public DocumentHeader readDocumentHeader(File filename) {

        RubyHash rubyHash = getParseHeaderOnlyOption();

        DocumentRuby documentRuby = this.asciidoctorModule.load_file(filename.getAbsolutePath(), rubyHash);
        return toDocumentHeader(documentRuby);
    }

    @SuppressWarnings("unchecked")
    @Override
    public DocumentHeader readDocumentHeader(String content) {

        RubyHash rubyHash = getParseHeaderOnlyOption();

        DocumentRuby documentRuby = this.asciidoctorModule.load(content, rubyHash);
        return toDocumentHeader(documentRuby);
    }

    @Override
    public DocumentHeader readDocumentHeader(Reader contentReader) {
        String content = IOUtils.readFull(contentReader);
        return this.readDocumentHeader(content);
    }

    private RubyHash getParseHeaderOnlyOption() {
        Map<String, Object> options = new HashMap<String, Object>();
        options.put("parse_header_only", true);
        RubyHash rubyHash = RubyHashUtil.convertMapToRubyHashWithSymbols(rubyRuntime, options);
        return rubyHash;
    }

    @SuppressWarnings("unchecked")
    @Override
    @Deprecated
    public String render(String content, Map<String, Object> options) {

        this.rubyGemsPreloader.preloadRequiredLibraries(options);

        logger.fine(AsciidoctorUtils.toAsciidoctorCommand(options, "-"));

        if (AsciidoctorUtils.isOptionWithAttribute(options, Attributes.SOURCE_HIGHLIGHTER, "pygments")) {
            logger.fine("In order to use Pygments with Asciidoctor, you need to install Pygments (and Python, if you don’t have it yet). Read http://asciidoctor.org/news/#syntax-highlighting-with-pygments.");
        }

        String currentDirectory = rubyRuntime.getCurrentDirectory();

        if (options.containsKey(Options.BASEDIR)) {
            rubyRuntime.setCurrentDirectory((String) options.get(Options.BASEDIR));
        }

        RubyHash rubyHash = RubyHashUtil.convertMapToRubyHashWithSymbols(rubyRuntime, options);

        try {
            Object object = this.asciidoctorModule.convert(content, rubyHash);
            return returnExpectedValue(object);
        } catch(RaiseException e) {
            logger.severe(e.getException().getClass().getCanonicalName());
            throw new AsciidoctorCoreException(e);
        } finally {
            // we restore current directory to its original value.
            rubyRuntime.setCurrentDirectory(currentDirectory);
        }

    }

    @SuppressWarnings("unchecked")
    @Override
    @Deprecated
    public String renderFile(File filename, Map<String, Object> options) {

        this.rubyGemsPreloader.preloadRequiredLibraries(options);

        logger.fine(AsciidoctorUtils.toAsciidoctorCommand(options, filename.getAbsolutePath()));

        String currentDirectory = rubyRuntime.getCurrentDirectory();

        if (options.containsKey(Options.BASEDIR)) {
            rubyRuntime.setCurrentDirectory((String) options.get(Options.BASEDIR));
        }

        RubyHash rubyHash = RubyHashUtil.convertMapToRubyHashWithSymbols(rubyRuntime, options);

        try {
            Object object = this.asciidoctorModule.convertFile(filename.getAbsolutePath(), rubyHash);
            return returnExpectedValue(object);
        } catch(RaiseException e) {
            logger.severe(e.getMessage());

            throw new AsciidoctorCoreException(e);
        } finally {
            // we restore current directory to its original value.
            rubyRuntime.setCurrentDirectory(currentDirectory);
        }
    }

    /**
     * This method has been added to deal with the fact that asciidoctor 0.1.2 can return an Asciidoctor::Document or a
     * String depending if content is write to disk or not. This may change in the future
     * (https://github.com/asciidoctor/asciidoctor/issues/286)
     * 
     * @param object
     * @return
     */
    private String returnExpectedValue(Object object) {
        if (object instanceof String) {
            return object.toString();
        } else {
            return null;
        }
    }

    @Override
    @Deprecated
    public void render(Reader contentReader, Writer rendererWriter, Map<String, Object> options) throws IOException {
        String content = IOUtils.readFull(contentReader);
        String renderedContent = render(content, options);
        IOUtils.writeFull(rendererWriter, renderedContent);
    }

    @Override
    @Deprecated
    public String[] renderFiles(Collection<File> asciidoctorFiles, Map<String, Object> options) {
        List<String> asciidoctorContent = renderAllFiles(options, asciidoctorFiles);
        return asciidoctorContent.toArray(new String[asciidoctorContent.size()]);
    }

    @Override
    @Deprecated
    public String[] renderFiles(Collection<File> asciidoctorFiles, Options options) {
        return this.renderFiles(asciidoctorFiles, options.map());
    }

    @Override
    @Deprecated
    public String[] renderDirectory(DirectoryWalker directoryWalker, Map<String, Object> options) {

        final List<File> asciidoctorFiles = scanForAsciiDocFiles(directoryWalker);
        List<String> asciidoctorContent = renderAllFiles(options, asciidoctorFiles);

        return asciidoctorContent.toArray(new String[asciidoctorContent.size()]);
    }

    private List<String> renderAllFiles(Map<String, Object> options, final Collection<File> asciidoctorFiles) {
        List<String> asciidoctorContent = new ArrayList<String>();

        for (File asciidoctorFile : asciidoctorFiles) {
            String renderedFile = renderFile(asciidoctorFile, options);

            if (renderedFile != null) {
                asciidoctorContent.add(renderedFile);
            }

        }

        return asciidoctorContent;
    }

    private List<File> scanForAsciiDocFiles(DirectoryWalker directoryWalker) {
        final List<File> asciidoctorFiles = directoryWalker.scan();
        return asciidoctorFiles;
    }

    @Override
    @Deprecated
    public String render(String content, Options options) {
        return this.render(content, options.map());
    }

    @Override
    @Deprecated
    public void render(Reader contentReader, Writer rendererWriter, Options options) throws IOException {
        this.render(contentReader, rendererWriter, options.map());
    }

    @Override
    @Deprecated
    public String renderFile(File filename, Options options) {
        return this.renderFile(filename, options.map());
    }

    @Override
    @Deprecated
    public String[] renderDirectory(DirectoryWalker directoryWalker, Options options) {
        return this.renderDirectory(directoryWalker, options.map());
    }

    @Override
    @Deprecated
    public String render(String content, OptionsBuilder options) {
        return this.render(content, options.asMap());
    }

    @Override
    @Deprecated
    public void render(Reader contentReader, Writer rendererWriter, OptionsBuilder options) throws IOException {
        this.render(contentReader, rendererWriter, options.asMap());
    }

    @Override
    @Deprecated
    public String renderFile(File filename, OptionsBuilder options) {
        return this.renderFile(filename, options.asMap());
    }

    @Override
    @Deprecated
    public String[] renderDirectory(DirectoryWalker directoryWalker, OptionsBuilder options) {
        return this.renderDirectory(directoryWalker, options.asMap());
    }

    @Override
    @Deprecated
    public String[] renderFiles(Collection<File> asciidoctorFiles, OptionsBuilder options) {
        return this.renderFiles(asciidoctorFiles, options.asMap());
    }

    @Override
    public void requireLibrary(String... library) {
        requireLibraries(Arrays.asList(library));
    }

    @Override
    public void requireLibraries(Collection<String> libraries) {
        if (libraries != null) {
            for (String library : libraries) {
                RubyUtils.requireLibrary(rubyRuntime, library);
            }
        }
    }

    @Override
    public JavaExtensionRegistry javaExtensionRegistry() {
        return new JavaExtensionRegistry(asciidoctorModule, rubyRuntime);
    }

    @Override
    public RubyExtensionRegistry rubyExtensionRegistry() {
        return new RubyExtensionRegistry(asciidoctorModule, rubyRuntime);
    }

    @Override
    public JavaConverterRegistry javaConverterRegistry() {
        return new JavaConverterRegistry(asciidoctorModule, rubyRuntime);
    }

    @Override
    public void unregisterAllExtensions() {
        this.asciidoctorModule.unregister_all_extensions();
    }

    @Override
    public void shutdown() {
        this.rubyRuntime.tearDown();
    }

    @Override
    public String asciidoctorVersion() {
        return this.asciidoctorModule.asciidoctorRuntimeEnvironmentVersion();
    }

    @Override
    public String convert(String content, Map<String, Object> options) {
        return render(content, options);
    }

    @Override
    public String convert(String content, Options options) {
        return render(content, options);
    }

    @Override
    public String convert(String content, OptionsBuilder options) {
        return render(content, options);
    }

    @Override
    public void convert(Reader contentReader, Writer rendererWriter, Map<String, Object> options) throws IOException {
        this.render(contentReader, rendererWriter, options);
    }

    @Override
    public void convert(Reader contentReader, Writer rendererWriter, Options options) throws IOException {
        this.render(contentReader, rendererWriter, options);
    }

    @Override
    public void convert(Reader contentReader, Writer rendererWriter, OptionsBuilder options) throws IOException {
        this.render(contentReader, rendererWriter, options);
    }

    @Override
    public String convertFile(File filename, Map<String, Object> options) {
        return renderFile(filename, options);
    }

    @Override
    public String convertFile(File filename, Options options) {
        return renderFile(filename, options);
    }

    @Override
    public String convertFile(File filename, OptionsBuilder options) {
        return renderFile(filename, options);
    }

    @Override
    public String[] convertDirectory(DirectoryWalker directoryWalker, Map<String, Object> options) {
        return renderDirectory(directoryWalker, options);
    }

    @Override
    public String[] convertDirectory(DirectoryWalker directoryWalker, Options options) {
        return renderDirectory(directoryWalker, options);
    }

    @Override
    public String[] convertDirectory(DirectoryWalker directoryWalker, OptionsBuilder options) {
        return renderDirectory(directoryWalker, options);
    }

    @Override
    public String[] convertFiles(Collection<File> asciidoctorFiles, Map<String, Object> options) {
        return renderFiles(asciidoctorFiles, options);
    }

    @Override
    public String[] convertFiles(Collection<File> asciidoctorFiles, Options options) {
        return renderFiles(asciidoctorFiles, options);
    }

    @Override
    public String[] convertFiles(Collection<File> asciidoctorFiles, OptionsBuilder options) {
        return renderFiles(asciidoctorFiles, options);
    }

    @Override
    public Document load(String content, Map<String, Object> options) {
        RubyHash rubyHash = RubyHashUtil.convertMapToRubyHashWithSymbols(rubyRuntime, options);
        return new Document(this.asciidoctorModule.load(content, rubyHash), this.rubyRuntime);
    }

    @Override
    public Document loadFile(File file, Map<String, Object> options) {
        RubyHash rubyHash = RubyHashUtil.convertMapToRubyHashWithSymbols(rubyRuntime, options);
        return new Document(this.asciidoctorModule.load(file.getAbsolutePath(), rubyHash), this.rubyRuntime);

    }

    static class LoggerOutputStream extends OutputStream {
        private final StringBuilder builder = new StringBuilder();
        private Logger logger;

        public LoggerOutputStream(Logger logger) {
            this.logger = logger;
        }

        private boolean doLog() {
            synchronized(this) {
                if (builder.length() > 0) {
                    String msg = builder.toString();
                    logger.info("My message: "+msg);
                    if(msg.contains("WARNING")) {
                        logger.logp(Level.WARNING, "", "", msg);
                    } else {
                        if(msg.contains("FAILED")) {
                            logger.logp(Level.SEVERE, "", "", msg);
                        } else {
                            logger.logp(Level.FINE, "", "", msg);
                        }
                    }
                    builder.setLength(0);
                    return true;
                }
                return false;
            }
        }

        @Override
        public void write(int b) throws IOException {
            if (b == '\n') {
                if (!doLog()) {
                    logger.info("");
                }
            } else {
                builder.append((char) b);
            }
        }

        @Override
        public void flush() throws IOException {
            doLog();
        }

        @Override
        public void close() throws IOException {
            doLog();
        }
    };

}
