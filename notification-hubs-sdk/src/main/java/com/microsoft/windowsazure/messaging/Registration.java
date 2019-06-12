/*
Copyright (c) Microsoft Open Technologies, Inc.
All Rights Reserved
Apache 2.0 License
 
   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at
 
     http://www.apache.org/licenses/LICENSE-2.0
 
   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 
See the Apache Version 2.0 License for specific language governing permissions and limitations under the License.
 */

package com.microsoft.windowsazure.messaging;

import java.io.IOException;
import java.io.StringReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public abstract class Registration {
  /**
   * Name for default registration
   */
  static final String DEFAULT_REGISTRATION_NAME = "$Default";

  /**
   * RegistrationId property for regid json object
   */
  static final String REGISTRATIONID_JSON_PROPERTY = "registrationid";

  /**
   * RegistrationName property for regid json object
   */
  static final String REGISTRATION_NAME_JSON_PROPERTY = "registrationName";

  /**
   * The Registration Id
   */
  protected String registrationId;

  /**
   * The Notification hub path
   */
  protected String notificationHubPath;

  /**
   * The expiration time
   */
  protected String expirationTime;

  /**
   * The PNS specific identifier
   */
  protected String pnsHandle;

  /**
   * The registration name
   */
  protected String name;

  /**
   * The registration tags
   */
  protected List<String> tags;

  /**
   * The registration URI
   */
  protected String uri;

  /**
   * The registration updated date
   */
  protected String updatedDate;

  /**
   * The registration ETag
   */
  protected String etag;

  /**
   * Creates an XML representation of the Registration
   */
  String toXml() throws Exception {
    DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
    builder.setEntityResolver(new EntityResolver() {

      @Override
      public InputSource resolveEntity(String publicId, String systemId) throws SAXException, IOException {
        return null;
      }
    });

    Document doc = builder.newDocument();

    Element entry = doc.createElement("entry");
    entry.setAttribute("xmlns", "http://www.w3.org/2005/Atom");
    doc.appendChild(entry);

    appendNodeWithValue(doc, entry, "id", getURI());
    appendNodeWithValue(doc, entry, "updated", getUpdatedString());
    appendContentNode(doc, entry);

    return Utils.getXmlString(doc.getDocumentElement());
  }

  /**
   * Appends the content node
   *
   * @param doc   The document to modify
   * @param entry The parent element
   */
  private void appendContentNode(Document doc, Element entry) {
    Element content = doc.createElement("content");
    content.setAttribute("type", "application/xml");
    entry.appendChild(content);

    Element registrationDescription = doc.createElement(getSpecificPayloadNodeName());
    registrationDescription.setAttribute("xmlns:i", "http://www.w3.org/2001/XMLSchema-instance");
    registrationDescription.setAttribute("xmlns", "http://schemas.microsoft.com/netservices/2010/10/servicebus/connect");
    content.appendChild(registrationDescription);

    appendNodeWithValue(doc, registrationDescription, "ETag", getETag());
    appendNodeWithValue(doc, registrationDescription, "ExpirationTime", getExpirationTimeString());
    appendNodeWithValue(doc, registrationDescription, "RegistrationId", getRegistrationId());
    appendTagsNode(doc, registrationDescription);

    appendCustomPayload(doc, registrationDescription);
  }

  /**
   * Appends a custom payload to the registration xml
   *
   * @param doc     The document to modify
   * @param content The parent element
   */
  protected abstract void appendCustomPayload(Document doc, Element content);

  /**
   * Appends the tags node to the registration xml
   *
   * @param doc                     The document to modify
   * @param registrationDescription The parent element
   */
  protected void appendTagsNode(Document doc, Element registrationDescription) {
    List<String> tagList = getTags();
    if (tagList != null && tagList.size() > 0) {
      StringBuilder tagsNodeValue = new StringBuilder(tagList.get(0));

      for (int i = 1; i < tagList.size(); i++) {
        tagsNodeValue.append(",").append(tagList.get(i));
      }

      Element tags = doc.createElement("Tags");
      tags.appendChild(doc.createTextNode(tagsNodeValue.toString()));
      registrationDescription.appendChild(tags);
    }
  }

  /**
   * Appends a node with a value to a registration xml
   *
   * @param doc           The document to modify
   * @param targetElement The parent element
   * @param nodeName      The node name
   * @param value         The node value
   */
  protected void appendNodeWithValue(Document doc, Element targetElement, String nodeName, String value) {
    if (!Utils.isNullOrWhiteSpace(value)) {
      Element newElement = doc.createElement(nodeName);
      newElement.appendChild(doc.createTextNode(value));
      targetElement.appendChild(newElement);
    }
  }

  /**
   * Fill the registration properties with the values found in an xml
   *
   * @param xml                 The xml to read
   * @param notificationHubPath The notificationHubPath
   */
  void loadXml(String xml, String notificationHubPath) throws Exception {
    DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
    Document doc = builder.parse(new InputSource(new StringReader(xml)));

    doc.getDocumentElement().normalize();
    Element root = doc.getDocumentElement();

    this.notificationHubPath = notificationHubPath;
    updatedDate = getNodeValue(root, "updated");

    NodeList payloadNodes = doc.getElementsByTagName(getSpecificPayloadNodeName());
    if (payloadNodes.getLength() > 0) {
      Element payloadNode = (Element) payloadNodes.item(0);
      etag = getNodeValue(payloadNode, "ETag");
      expirationTime = getNodeValue(payloadNode, "ExpirationTime");
      registrationId = getNodeValue(payloadNode, "RegistrationId");
      uri = notificationHubPath + "/Registrations/" + registrationId;

      String tags = getNodeValue(payloadNode, "Tags");
      if (!Utils.isNullOrWhiteSpace(tags)) {
        tags = tags.trim();
        String[] tagList = tags.split(",");

        Collections.addAll(this.tags, tagList);
      }

      loadCustomXmlData(payloadNode);
    }
  }

  /**
   * Loads custom data for a specific registration type
   *
   * @param payloadNode The xml node to read
   */
  protected abstract void loadCustomXmlData(Element payloadNode);

  /**
   * Gets the custom payload name for a specific registration type
   *
   * @return String object
   */
  protected abstract String getSpecificPayloadNodeName();

  /**
   * Get the node value
   *
   * @param element The element to read
   * @param node    The node name to retrieve
   * @return String object
   */
  protected static String getNodeValue(Element element, String node) {
    NodeList nodes = element.getElementsByTagName(node);
    if (nodes.getLength() > 0) {
      return nodes.item(0).getTextContent();
    } else {
      return null;
    }
  }

  /**
   * Creates a new registration
   *
   * @param notificationHubPath The notification hub path
   */
  Registration(String notificationHubPath) {
    tags = new ArrayList<String>();
    this.notificationHubPath = notificationHubPath;
  }

  /**
   * Gets the registration ID
   */
  public String getRegistrationId() {
    return registrationId;
  }

  /**
   * Sets the registration ID
   */
  void setRegistrationId(String registrationId) {
    this.registrationId = registrationId;
  }

  /**
   * Gets the notification hub path
   */
  public String getNotificationHubPath() {
    return notificationHubPath;
  }

  /**
   * Sets the notification hub path
   */
  void setNotificationHubPath(String notificationHubPath) {
    this.notificationHubPath = notificationHubPath;
  }

  /**
   * Gets the registration name
   */
  String getName() {
    return name;
  }

  /**
   * Sets the registration name
   */
  void setName(String name) {
    this.name = name;
  }

  /**
   * Gets the registration tags
   */
  public List<String> getTags() {
    return new ArrayList<String>(tags);
  }

  /**
   * Gets the registration URI
   */
  public String getURI() {
    return getNotificationHubPath() + "/Registrations/" + registrationId;
  }

  /**
   * Gets the registration ETag
   */
  String getETag() {
    return etag;
  }

  /**
   * Sets the registration etag
   */
  void setETag(String eTag) {
    etag = eTag;
  }

  /**
   * Parses an UTC date string into a Date object
   *
   * @param dateString The date string to parse
   * @return The Date object
   */
  private static Date UTCDateStringToDate(String dateString) throws ParseException {
    // Change Z to +00:00 to adapt the string to a format
    // that can be parsed in Java
    String s = dateString.replace("Z", "+00:00");
    try {
      // Remove the ":" character to adapt the string to a
      // format that can be parsed in Java
      s = s.substring(0, 26) + s.substring(27);
    } catch (IndexOutOfBoundsException e) {
      throw new ParseException("The 'updated' value has an invalid format", 26);
    }

    // Parse the well-formatted date string
    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'.'SSSZ", Locale.getDefault());
    dateFormat.setTimeZone(TimeZone.getDefault());
    return dateFormat.parse(s);
  }

  /**
   * Gets the update date
   */
  Date getUpdated() throws ParseException {
    return UTCDateStringToDate(updatedDate);
  }

  /**
   * Gets the updated date string
   */
  String getUpdatedString() {
    return updatedDate;
  }

  /**
   * Sets the updated date string
   */
  void setUpdatedString(String updatedDateString) {
    updatedDate = updatedDateString;
  }

  /**
   * Gets the PNS specific identifier
   */
  public String getPNSHandle() {
    return pnsHandle;
  }

  /**
   * Sets the PNS specific identifier
   */
  void setPNSHandle(String pNSHandle) {
    pnsHandle = pNSHandle;
  }

  /**
   * Gets the expiration time
   */
  public Date getExpirationTime() throws ParseException {
    return UTCDateStringToDate(expirationTime);
  }

  /**
   * Gets the expiration time string
   */
  String getExpirationTimeString() {
    return expirationTime;
  }

  /**
   * Sets the expiration time string
   */
  void setExpirationTimeString(String expirationTimeString) {
    expirationTime = expirationTimeString;
  }

  /**
   * Adds the tags in the array to the registration
   */
  void addTags(String[] tags) {
    if (tags != null) {
      for (String tag : tags) {
        if (!Utils.isNullOrWhiteSpace(tag)) {
          this.tags.add(tag);
        }
      }
    }
  }

  /**
   * Gets the registration information JSON object
   */
  JSONObject getRegistrationInformation() throws JSONException {
    JSONObject regInfo = new JSONObject();
    regInfo.put(REGISTRATIONID_JSON_PROPERTY, getRegistrationId());
    regInfo.put(REGISTRATION_NAME_JSON_PROPERTY, getName());

    return regInfo;
  }

}