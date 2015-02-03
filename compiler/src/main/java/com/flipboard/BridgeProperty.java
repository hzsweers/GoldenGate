package com.flipboard;

import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;

import java.util.Base64;
import java.util.Random;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.VariableElement;

public class BridgeProperty {

    public final String javaName;
    public final String name;
    public final BridgeParameter parameter;
    public final BridgeCallback callback;

    public BridgeProperty(ExecutableElement e) {
        this.javaName = e.getSimpleName().toString();

        String name = e.getAnnotation(Property.class).value();
        VariableElement param = e.getParameters().get(0);
        this.name = "".equals(name) ? param.getSimpleName().toString() : name;

        if (Util.isCallback(param)) {
            parameter = null;
            callback = new BridgeCallback(param);
        } else {
            parameter = new BridgeParameter(param);
            callback = null;
        }
    }

    public MethodSpec toMethodSpec(String bridgeName) {
        MethodSpec.Builder methodSpec = MethodSpec.methodBuilder(javaName).addModifiers(Modifier.PUBLIC);

        if (callback != null) {
            methodSpec.addParameter(TypeName.get(callback.type), callback.name, Modifier.FINAL);
            methodSpec.addCode(CodeBlock.builder()
                    .addStatement("$T uuid = randomUUID()", String.class)
                    .add("this.resultBridge.registerCallback(uuid, new Callback<String>() {\n")
                    .indent()
                    .add("@$T\n", Override.class)
                    .add("public void onResult(String result) {\n")
                    .indent()
                    .addStatement("$N.onResult(fromJson(result, $T.class))", callback.name, callback.genericType)
                    .unindent()
                    .add("}\n")
                    .unindent()
                    .addStatement("})")
                    .build());

            methodSpec.addCode(CodeBlock.builder()
                    .addStatement("this.webView.loadUrl(\"javascript:$L.onResult(JSON.stringify({receiver:\"+uuid+\", result:$L()});\")", bridgeName, name)
                    .build());
        } else {
            methodSpec.addParameter(TypeName.get(parameter.type), parameter.name, Modifier.FINAL);
            methodSpec.addCode(CodeBlock.builder()
                    .addStatement("this.webView.loadUrl(\"javascript:$L(\"+toJson($L)+\");\")", name, parameter.name)
                    .build());
        }

        return methodSpec.build();
    }

}