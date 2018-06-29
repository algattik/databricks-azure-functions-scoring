package com.fabrikam.functions;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import ml.combust.mleap.core.types.StructField;
import ml.combust.mleap.core.types.StructType;
import ml.combust.mleap.runtime.MleapContext;
import ml.combust.mleap.runtime.frame.DefaultLeapFrame;
import ml.combust.mleap.runtime.frame.Row;
import ml.combust.mleap.runtime.frame.Transformer;
import ml.combust.mleap.runtime.javadsl.BundleBuilder;
import ml.combust.mleap.runtime.javadsl.ContextBuilder;
import ml.combust.mleap.runtime.javadsl.LeapFrameBuilder;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


/**
 * Azure Functions with HTTP Trigger.
 */
public class Function {


    private static final Transformer mleapPipeline;

    private static final LeapFrameBuilder builder;

    private static final StructType schema;

    // Read MLeap model from classpath into a temporary directory and load it
    static {
        String modelPathInClassPath = "/20news_pipeline.zip";

        URL inputUrl = Function.class.getResource(modelPathInClassPath);
        File dest;
        try {
            dest = File.createTempFile("mleap", ".zip");
            dest.deleteOnExit();
            FileUtils.copyURLToFile(inputUrl, dest);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        builder = new LeapFrameBuilder();
        List<StructField> fields = new ArrayList<StructField>();
        fields.add(builder.createField("topic", builder.createString()));
        fields.add(builder.createField("text", builder.createString()));
        schema = builder.createSchema(fields);

        MleapContext mleapContext = new ContextBuilder().createMleapContext();
        BundleBuilder bundleBuilder = new BundleBuilder();
        mleapPipeline = bundleBuilder.load(dest, mleapContext).root();
    }

    /**
     * This function listens at endpoint "/api/predictTopic". Two ways to invoke it using "curl" command in bash:
     * 1. curl -d "HTTP Body" {your host}/api/predictTopic
     * 2. curl {your host}/api/predictTopic?text=HTTP%20Query
     */
    @FunctionName("predictTopic")
    public HttpResponseMessage<String> predictTopic(
            @HttpTrigger(name = "req", methods = {"get", "post"}, authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {
        context.getLogger().info("Java HTTP trigger processed a request.");

        // Parse query parameter
        String query = request.getQueryParameters().get("text");
        String text = request.getBody().orElse(query);

        List<Row> rows = new ArrayList<Row>();
        rows.add(builder.createRow("unknown", query));
        DefaultLeapFrame input = builder.createFrame(schema, rows);

        DefaultLeapFrame output = mleapPipeline.transform(input).get();
        Row row0 = output.dataset().iterator().next();
        String predicted = row0.getString(8);

        if (text == null) {
            return request.createResponse(400, "Please pass a text on the query string or in the request body");
        } else {
            return request.createResponse(200, predicted);
        }
    }
}
