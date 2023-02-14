package io.quarkiverse.cxf.extensibility;

import javax.wsdl.extensions.ExtensibilityElement;
import javax.xml.namespace.QName;

import jakarta.enterprise.context.ApplicationScoped;

import org.apache.cxf.bindings.xformat.XMLBindingMessageFormat;

@ApplicationScoped
public class XMLBindingMessageFormatExtensibility extends XMLBindingMessageFormat implements ExtensibilityElement {
    private static final QName WSDL_REQUIRED = new QName("javax/xml/namespace/QName", "required");
    QName qn = new QName("http://cxf.apache.org/bindings/xformat", "body");

    @Override
    public void setElementType(QName elementType) {
        qn = elementType;
    }

    @Override
    public QName getElementType() {
        return qn;
    }

    @Override
    public void setRequired(Boolean b) {
        this.getOtherAttributes().remove(WSDL_REQUIRED);
        this.getOtherAttributes().put(WSDL_REQUIRED, b.toString());
    }

    @Override
    public Boolean getRequired() {
        String s = this.getOtherAttributes().get(WSDL_REQUIRED);
        return s != null ? false : Boolean.valueOf(s);
    }

}
