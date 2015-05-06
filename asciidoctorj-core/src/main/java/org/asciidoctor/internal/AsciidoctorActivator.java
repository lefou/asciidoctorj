package org.asciidoctor.internal;

import java.util.Dictionary;
import java.util.Properties;

import org.asciidoctor.Asciidoctor;
import org.jruby.Ruby;
import org.jruby.embed.osgi.OSGiScriptingContainer;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

public class AsciidoctorActivator implements BundleActivator {

	private Asciidoctor asciidoctor;

	@Override
	public void start(final BundleContext bundleContext) throws Exception {
		final String asciidoctorGemVersion = (String) bundleContext.getBundle().getHeaders().get("Asciidoctor-AsciidoctorGemVersion");
		if (asciidoctorGemVersion != null) {
			final OSGiScriptingContainer container = new OSGiScriptingContainer(bundleContext.getBundle());

			// final Map<String, Object> env = new HashMap<String, Object>();
			// env.put(GEM_PATH, gemPath);
			// JRubyAsciidoctor.injectEnvironmentVariables(container.getProvider().getRubyInstanceConfig(),
			// env);
			// JavaEmbedUtils.initialize(Arrays.asList());

			final Ruby rubyRuntime = container.getProvider().getRuntime();

			JRubyRuntimeContext.set(rubyRuntime);

			final JRubyAsciidoctorModuleFactory jRubyAsciidoctorModuleFactory = new JRubyAsciidoctorModuleFactory(rubyRuntime);

			final AsciidoctorModule asciidoctorModule = jRubyAsciidoctorModuleFactory.createAsciidoctorModule();
			asciidoctor = new JRubyAsciidoctor(asciidoctorModule, rubyRuntime);

			final Dictionary<Object, Object> props = new Properties();
			props.put("AsciidoctorGemVersion", asciidoctorGemVersion);

			bundleContext.registerService(Asciidoctor.class.getName(), asciidoctor, props);

		}
	}

	@Override
	public void stop(final BundleContext bundleContext) throws Exception {
		asciidoctor.shutdown();
	}

}
