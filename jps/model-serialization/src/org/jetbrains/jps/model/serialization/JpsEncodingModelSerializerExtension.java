package org.jetbrains.jps.model.serialization;

import com.intellij.openapi.util.JDOMUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsEncodingConfigurationService;
import org.jetbrains.jps.model.JpsGlobal;
import org.jetbrains.jps.model.JpsProject;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author nik
 */
public class JpsEncodingModelSerializerExtension extends JpsModelSerializerExtension {
  @NotNull
  @Override
  public List<? extends JpsProjectExtensionSerializer> getProjectExtensionSerializers() {
    return Arrays.asList(new JpsEncodingConfigurationSerializer());
  }

  @NotNull
  @Override
  public List<? extends JpsGlobalExtensionSerializer> getGlobalExtensionSerializers() {
    return Arrays.asList(new JpsGlobalEncodingSerializer());
  }

  private static class JpsEncodingConfigurationSerializer extends JpsProjectExtensionSerializer {
    private JpsEncodingConfigurationSerializer() {
      super("encodings.xml", "Encoding");
    }

    @Override
    public void loadExtension(@NotNull JpsProject project, @NotNull Element componentTag) {
      String projectEncoding = null;
      Map<String, String> urlToEncoding = new HashMap<String, String>();
      for (Element fileTag : JDOMUtil.getChildren(componentTag, "file")) {
        String url = fileTag.getAttributeValue("url");
        String encoding = fileTag.getAttributeValue("charset");
        if (url.equals("PROJECT")) {
          projectEncoding = encoding;
        }
        else {
          urlToEncoding.put(url, encoding);
        }
      }
      JpsEncodingConfigurationService.getInstance().setEncodingConfiguration(project, projectEncoding, urlToEncoding);
    }

    @Override
    public void saveExtension(@NotNull JpsProject project, @NotNull Element componentTag) {
    }
  }

  private static class JpsGlobalEncodingSerializer extends JpsGlobalExtensionSerializer {
    public static final String ENCODING_ATTRIBUTE = "default_encoding";
    
    private JpsGlobalEncodingSerializer() {
      super("encoding.xml", "Encoding");
    }

    @Override
    public void loadExtension(@NotNull JpsGlobal global, @NotNull Element componentTag) {
      JpsEncodingConfigurationService.getInstance().setGlobalEncoding(global, componentTag.getAttributeValue(ENCODING_ATTRIBUTE));
    }

    @Override
    public void saveExtension(@NotNull JpsGlobal global, @NotNull Element componentTag) {
      componentTag.setAttribute(ENCODING_ATTRIBUTE, JpsEncodingConfigurationService.getInstance().getGlobalEncoding(global));
    }
  }
}
