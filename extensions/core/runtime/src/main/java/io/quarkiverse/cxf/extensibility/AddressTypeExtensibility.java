package io.quarkiverse.cxf.extensibility;

import javax.wsdl.extensions.ExtensibilityElement;
import javax.xml.namespace.QName;

import jakarta.enterprise.context.ApplicationScoped;

import org.apache.cxf.wsdl.http.AddressType;

@ApplicationScoped
public class AddressTypeExtensibility extends AddressType implements ExtensibilityElement {
    private static final QName WSDL_REQUIRED = new QName("javax/xml/namespace/QName", "required");
    QName qn = new QName("http://schemas.xmlsoap.org/wsdl/http/", "address");

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
