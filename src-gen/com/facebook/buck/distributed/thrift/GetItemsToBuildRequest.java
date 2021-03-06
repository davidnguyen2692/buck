/**
 * Autogenerated by Thrift Compiler (0.9.3)
 *
 * DO NOT EDIT UNLESS YOU ARE SURE THAT YOU KNOW WHAT YOU ARE DOING
 *  @generated
 */
package com.facebook.buck.distributed.thrift;

import org.apache.thrift.scheme.IScheme;
import org.apache.thrift.scheme.SchemeFactory;
import org.apache.thrift.scheme.StandardScheme;

import org.apache.thrift.scheme.TupleScheme;
import org.apache.thrift.protocol.TTupleProtocol;
import org.apache.thrift.protocol.TProtocolException;
import org.apache.thrift.EncodingUtils;
import org.apache.thrift.TException;
import org.apache.thrift.async.AsyncMethodCallback;
import org.apache.thrift.server.AbstractNonblockingServer.*;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.EnumMap;
import java.util.Set;
import java.util.HashSet;
import java.util.EnumSet;
import java.util.Collections;
import java.util.BitSet;
import java.nio.ByteBuffer;
import java.util.Arrays;
import javax.annotation.Generated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings({"cast", "rawtypes", "serial", "unchecked"})
@Generated(value = "Autogenerated by Thrift Compiler (0.9.3)", date = "2016-12-05")
public class GetItemsToBuildRequest implements org.apache.thrift.TBase<GetItemsToBuildRequest, GetItemsToBuildRequest._Fields>, java.io.Serializable, Cloneable, Comparable<GetItemsToBuildRequest> {
  private static final org.apache.thrift.protocol.TStruct STRUCT_DESC = new org.apache.thrift.protocol.TStruct("GetItemsToBuildRequest");

  private static final org.apache.thrift.protocol.TField BUILDER_ID_FIELD_DESC = new org.apache.thrift.protocol.TField("builderId", org.apache.thrift.protocol.TType.STRING, (short)1);

  private static final Map<Class<? extends IScheme>, SchemeFactory> schemes = new HashMap<Class<? extends IScheme>, SchemeFactory>();
  static {
    schemes.put(StandardScheme.class, new GetItemsToBuildRequestStandardSchemeFactory());
    schemes.put(TupleScheme.class, new GetItemsToBuildRequestTupleSchemeFactory());
  }

  public String builderId; // optional

  /** The set of fields this struct contains, along with convenience methods for finding and manipulating them. */
  public enum _Fields implements org.apache.thrift.TFieldIdEnum {
    BUILDER_ID((short)1, "builderId");

    private static final Map<String, _Fields> byName = new HashMap<String, _Fields>();

    static {
      for (_Fields field : EnumSet.allOf(_Fields.class)) {
        byName.put(field.getFieldName(), field);
      }
    }

    /**
     * Find the _Fields constant that matches fieldId, or null if its not found.
     */
    public static _Fields findByThriftId(int fieldId) {
      switch(fieldId) {
        case 1: // BUILDER_ID
          return BUILDER_ID;
        default:
          return null;
      }
    }

    /**
     * Find the _Fields constant that matches fieldId, throwing an exception
     * if it is not found.
     */
    public static _Fields findByThriftIdOrThrow(int fieldId) {
      _Fields fields = findByThriftId(fieldId);
      if (fields == null) throw new IllegalArgumentException("Field " + fieldId + " doesn't exist!");
      return fields;
    }

    /**
     * Find the _Fields constant that matches name, or null if its not found.
     */
    public static _Fields findByName(String name) {
      return byName.get(name);
    }

    private final short _thriftId;
    private final String _fieldName;

    _Fields(short thriftId, String fieldName) {
      _thriftId = thriftId;
      _fieldName = fieldName;
    }

    public short getThriftFieldId() {
      return _thriftId;
    }

    public String getFieldName() {
      return _fieldName;
    }
  }

  // isset id assignments
  private static final _Fields optionals[] = {_Fields.BUILDER_ID};
  public static final Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> metaDataMap;
  static {
    Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> tmpMap = new EnumMap<_Fields, org.apache.thrift.meta_data.FieldMetaData>(_Fields.class);
    tmpMap.put(_Fields.BUILDER_ID, new org.apache.thrift.meta_data.FieldMetaData("builderId", org.apache.thrift.TFieldRequirementType.OPTIONAL, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.STRING)));
    metaDataMap = Collections.unmodifiableMap(tmpMap);
    org.apache.thrift.meta_data.FieldMetaData.addStructMetaDataMap(GetItemsToBuildRequest.class, metaDataMap);
  }

  public GetItemsToBuildRequest() {
  }

  /**
   * Performs a deep copy on <i>other</i>.
   */
  public GetItemsToBuildRequest(GetItemsToBuildRequest other) {
    if (other.isSetBuilderId()) {
      this.builderId = other.builderId;
    }
  }

  public GetItemsToBuildRequest deepCopy() {
    return new GetItemsToBuildRequest(this);
  }

  @Override
  public void clear() {
    this.builderId = null;
  }

  public String getBuilderId() {
    return this.builderId;
  }

  public GetItemsToBuildRequest setBuilderId(String builderId) {
    this.builderId = builderId;
    return this;
  }

  public void unsetBuilderId() {
    this.builderId = null;
  }

  /** Returns true if field builderId is set (has been assigned a value) and false otherwise */
  public boolean isSetBuilderId() {
    return this.builderId != null;
  }

  public void setBuilderIdIsSet(boolean value) {
    if (!value) {
      this.builderId = null;
    }
  }

  public void setFieldValue(_Fields field, Object value) {
    switch (field) {
    case BUILDER_ID:
      if (value == null) {
        unsetBuilderId();
      } else {
        setBuilderId((String)value);
      }
      break;

    }
  }

  public Object getFieldValue(_Fields field) {
    switch (field) {
    case BUILDER_ID:
      return getBuilderId();

    }
    throw new IllegalStateException();
  }

  /** Returns true if field corresponding to fieldID is set (has been assigned a value) and false otherwise */
  public boolean isSet(_Fields field) {
    if (field == null) {
      throw new IllegalArgumentException();
    }

    switch (field) {
    case BUILDER_ID:
      return isSetBuilderId();
    }
    throw new IllegalStateException();
  }

  @Override
  public boolean equals(Object that) {
    if (that == null)
      return false;
    if (that instanceof GetItemsToBuildRequest)
      return this.equals((GetItemsToBuildRequest)that);
    return false;
  }

  public boolean equals(GetItemsToBuildRequest that) {
    if (that == null)
      return false;

    boolean this_present_builderId = true && this.isSetBuilderId();
    boolean that_present_builderId = true && that.isSetBuilderId();
    if (this_present_builderId || that_present_builderId) {
      if (!(this_present_builderId && that_present_builderId))
        return false;
      if (!this.builderId.equals(that.builderId))
        return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    List<Object> list = new ArrayList<Object>();

    boolean present_builderId = true && (isSetBuilderId());
    list.add(present_builderId);
    if (present_builderId)
      list.add(builderId);

    return list.hashCode();
  }

  @Override
  public int compareTo(GetItemsToBuildRequest other) {
    if (!getClass().equals(other.getClass())) {
      return getClass().getName().compareTo(other.getClass().getName());
    }

    int lastComparison = 0;

    lastComparison = Boolean.valueOf(isSetBuilderId()).compareTo(other.isSetBuilderId());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetBuilderId()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.builderId, other.builderId);
      if (lastComparison != 0) {
        return lastComparison;
      }
    }
    return 0;
  }

  public _Fields fieldForId(int fieldId) {
    return _Fields.findByThriftId(fieldId);
  }

  public void read(org.apache.thrift.protocol.TProtocol iprot) throws org.apache.thrift.TException {
    schemes.get(iprot.getScheme()).getScheme().read(iprot, this);
  }

  public void write(org.apache.thrift.protocol.TProtocol oprot) throws org.apache.thrift.TException {
    schemes.get(oprot.getScheme()).getScheme().write(oprot, this);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("GetItemsToBuildRequest(");
    boolean first = true;

    if (isSetBuilderId()) {
      sb.append("builderId:");
      if (this.builderId == null) {
        sb.append("null");
      } else {
        sb.append(this.builderId);
      }
      first = false;
    }
    sb.append(")");
    return sb.toString();
  }

  public void validate() throws org.apache.thrift.TException {
    // check for required fields
    // check for sub-struct validity
  }

  private void writeObject(java.io.ObjectOutputStream out) throws java.io.IOException {
    try {
      write(new org.apache.thrift.protocol.TCompactProtocol(new org.apache.thrift.transport.TIOStreamTransport(out)));
    } catch (org.apache.thrift.TException te) {
      throw new java.io.IOException(te);
    }
  }

  private void readObject(java.io.ObjectInputStream in) throws java.io.IOException, ClassNotFoundException {
    try {
      read(new org.apache.thrift.protocol.TCompactProtocol(new org.apache.thrift.transport.TIOStreamTransport(in)));
    } catch (org.apache.thrift.TException te) {
      throw new java.io.IOException(te);
    }
  }

  private static class GetItemsToBuildRequestStandardSchemeFactory implements SchemeFactory {
    public GetItemsToBuildRequestStandardScheme getScheme() {
      return new GetItemsToBuildRequestStandardScheme();
    }
  }

  private static class GetItemsToBuildRequestStandardScheme extends StandardScheme<GetItemsToBuildRequest> {

    public void read(org.apache.thrift.protocol.TProtocol iprot, GetItemsToBuildRequest struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TField schemeField;
      iprot.readStructBegin();
      while (true)
      {
        schemeField = iprot.readFieldBegin();
        if (schemeField.type == org.apache.thrift.protocol.TType.STOP) { 
          break;
        }
        switch (schemeField.id) {
          case 1: // BUILDER_ID
            if (schemeField.type == org.apache.thrift.protocol.TType.STRING) {
              struct.builderId = iprot.readString();
              struct.setBuilderIdIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          default:
            org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
        }
        iprot.readFieldEnd();
      }
      iprot.readStructEnd();

      // check for required fields of primitive type, which can't be checked in the validate method
      struct.validate();
    }

    public void write(org.apache.thrift.protocol.TProtocol oprot, GetItemsToBuildRequest struct) throws org.apache.thrift.TException {
      struct.validate();

      oprot.writeStructBegin(STRUCT_DESC);
      if (struct.builderId != null) {
        if (struct.isSetBuilderId()) {
          oprot.writeFieldBegin(BUILDER_ID_FIELD_DESC);
          oprot.writeString(struct.builderId);
          oprot.writeFieldEnd();
        }
      }
      oprot.writeFieldStop();
      oprot.writeStructEnd();
    }

  }

  private static class GetItemsToBuildRequestTupleSchemeFactory implements SchemeFactory {
    public GetItemsToBuildRequestTupleScheme getScheme() {
      return new GetItemsToBuildRequestTupleScheme();
    }
  }

  private static class GetItemsToBuildRequestTupleScheme extends TupleScheme<GetItemsToBuildRequest> {

    @Override
    public void write(org.apache.thrift.protocol.TProtocol prot, GetItemsToBuildRequest struct) throws org.apache.thrift.TException {
      TTupleProtocol oprot = (TTupleProtocol) prot;
      BitSet optionals = new BitSet();
      if (struct.isSetBuilderId()) {
        optionals.set(0);
      }
      oprot.writeBitSet(optionals, 1);
      if (struct.isSetBuilderId()) {
        oprot.writeString(struct.builderId);
      }
    }

    @Override
    public void read(org.apache.thrift.protocol.TProtocol prot, GetItemsToBuildRequest struct) throws org.apache.thrift.TException {
      TTupleProtocol iprot = (TTupleProtocol) prot;
      BitSet incoming = iprot.readBitSet(1);
      if (incoming.get(0)) {
        struct.builderId = iprot.readString();
        struct.setBuilderIdIsSet(true);
      }
    }
  }

}

