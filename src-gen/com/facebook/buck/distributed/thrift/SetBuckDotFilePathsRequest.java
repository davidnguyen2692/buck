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
@Generated(value = "Autogenerated by Thrift Compiler (0.9.3)", date = "2017-01-30")
public class SetBuckDotFilePathsRequest implements org.apache.thrift.TBase<SetBuckDotFilePathsRequest, SetBuckDotFilePathsRequest._Fields>, java.io.Serializable, Cloneable, Comparable<SetBuckDotFilePathsRequest> {
  private static final org.apache.thrift.protocol.TStruct STRUCT_DESC = new org.apache.thrift.protocol.TStruct("SetBuckDotFilePathsRequest");

  private static final org.apache.thrift.protocol.TField BUILD_ID_FIELD_DESC = new org.apache.thrift.protocol.TField("buildId", org.apache.thrift.protocol.TType.STRUCT, (short)1);
  private static final org.apache.thrift.protocol.TField DOT_FILES_FIELD_DESC = new org.apache.thrift.protocol.TField("dotFiles", org.apache.thrift.protocol.TType.LIST, (short)2);

  private static final Map<Class<? extends IScheme>, SchemeFactory> schemes = new HashMap<Class<? extends IScheme>, SchemeFactory>();
  static {
    schemes.put(StandardScheme.class, new SetBuckDotFilePathsRequestStandardSchemeFactory());
    schemes.put(TupleScheme.class, new SetBuckDotFilePathsRequestTupleSchemeFactory());
  }

  public BuildId buildId; // optional
  public List<PathInfo> dotFiles; // optional

  /** The set of fields this struct contains, along with convenience methods for finding and manipulating them. */
  public enum _Fields implements org.apache.thrift.TFieldIdEnum {
    BUILD_ID((short)1, "buildId"),
    DOT_FILES((short)2, "dotFiles");

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
        case 1: // BUILD_ID
          return BUILD_ID;
        case 2: // DOT_FILES
          return DOT_FILES;
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
  private static final _Fields optionals[] = {_Fields.BUILD_ID,_Fields.DOT_FILES};
  public static final Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> metaDataMap;
  static {
    Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> tmpMap = new EnumMap<_Fields, org.apache.thrift.meta_data.FieldMetaData>(_Fields.class);
    tmpMap.put(_Fields.BUILD_ID, new org.apache.thrift.meta_data.FieldMetaData("buildId", org.apache.thrift.TFieldRequirementType.OPTIONAL, 
        new org.apache.thrift.meta_data.StructMetaData(org.apache.thrift.protocol.TType.STRUCT, BuildId.class)));
    tmpMap.put(_Fields.DOT_FILES, new org.apache.thrift.meta_data.FieldMetaData("dotFiles", org.apache.thrift.TFieldRequirementType.OPTIONAL, 
        new org.apache.thrift.meta_data.ListMetaData(org.apache.thrift.protocol.TType.LIST, 
            new org.apache.thrift.meta_data.StructMetaData(org.apache.thrift.protocol.TType.STRUCT, PathInfo.class))));
    metaDataMap = Collections.unmodifiableMap(tmpMap);
    org.apache.thrift.meta_data.FieldMetaData.addStructMetaDataMap(SetBuckDotFilePathsRequest.class, metaDataMap);
  }

  public SetBuckDotFilePathsRequest() {
  }

  /**
   * Performs a deep copy on <i>other</i>.
   */
  public SetBuckDotFilePathsRequest(SetBuckDotFilePathsRequest other) {
    if (other.isSetBuildId()) {
      this.buildId = new BuildId(other.buildId);
    }
    if (other.isSetDotFiles()) {
      List<PathInfo> __this__dotFiles = new ArrayList<PathInfo>(other.dotFiles.size());
      for (PathInfo other_element : other.dotFiles) {
        __this__dotFiles.add(new PathInfo(other_element));
      }
      this.dotFiles = __this__dotFiles;
    }
  }

  public SetBuckDotFilePathsRequest deepCopy() {
    return new SetBuckDotFilePathsRequest(this);
  }

  @Override
  public void clear() {
    this.buildId = null;
    this.dotFiles = null;
  }

  public BuildId getBuildId() {
    return this.buildId;
  }

  public SetBuckDotFilePathsRequest setBuildId(BuildId buildId) {
    this.buildId = buildId;
    return this;
  }

  public void unsetBuildId() {
    this.buildId = null;
  }

  /** Returns true if field buildId is set (has been assigned a value) and false otherwise */
  public boolean isSetBuildId() {
    return this.buildId != null;
  }

  public void setBuildIdIsSet(boolean value) {
    if (!value) {
      this.buildId = null;
    }
  }

  public int getDotFilesSize() {
    return (this.dotFiles == null) ? 0 : this.dotFiles.size();
  }

  public java.util.Iterator<PathInfo> getDotFilesIterator() {
    return (this.dotFiles == null) ? null : this.dotFiles.iterator();
  }

  public void addToDotFiles(PathInfo elem) {
    if (this.dotFiles == null) {
      this.dotFiles = new ArrayList<PathInfo>();
    }
    this.dotFiles.add(elem);
  }

  public List<PathInfo> getDotFiles() {
    return this.dotFiles;
  }

  public SetBuckDotFilePathsRequest setDotFiles(List<PathInfo> dotFiles) {
    this.dotFiles = dotFiles;
    return this;
  }

  public void unsetDotFiles() {
    this.dotFiles = null;
  }

  /** Returns true if field dotFiles is set (has been assigned a value) and false otherwise */
  public boolean isSetDotFiles() {
    return this.dotFiles != null;
  }

  public void setDotFilesIsSet(boolean value) {
    if (!value) {
      this.dotFiles = null;
    }
  }

  public void setFieldValue(_Fields field, Object value) {
    switch (field) {
    case BUILD_ID:
      if (value == null) {
        unsetBuildId();
      } else {
        setBuildId((BuildId)value);
      }
      break;

    case DOT_FILES:
      if (value == null) {
        unsetDotFiles();
      } else {
        setDotFiles((List<PathInfo>)value);
      }
      break;

    }
  }

  public Object getFieldValue(_Fields field) {
    switch (field) {
    case BUILD_ID:
      return getBuildId();

    case DOT_FILES:
      return getDotFiles();

    }
    throw new IllegalStateException();
  }

  /** Returns true if field corresponding to fieldID is set (has been assigned a value) and false otherwise */
  public boolean isSet(_Fields field) {
    if (field == null) {
      throw new IllegalArgumentException();
    }

    switch (field) {
    case BUILD_ID:
      return isSetBuildId();
    case DOT_FILES:
      return isSetDotFiles();
    }
    throw new IllegalStateException();
  }

  @Override
  public boolean equals(Object that) {
    if (that == null)
      return false;
    if (that instanceof SetBuckDotFilePathsRequest)
      return this.equals((SetBuckDotFilePathsRequest)that);
    return false;
  }

  public boolean equals(SetBuckDotFilePathsRequest that) {
    if (that == null)
      return false;

    boolean this_present_buildId = true && this.isSetBuildId();
    boolean that_present_buildId = true && that.isSetBuildId();
    if (this_present_buildId || that_present_buildId) {
      if (!(this_present_buildId && that_present_buildId))
        return false;
      if (!this.buildId.equals(that.buildId))
        return false;
    }

    boolean this_present_dotFiles = true && this.isSetDotFiles();
    boolean that_present_dotFiles = true && that.isSetDotFiles();
    if (this_present_dotFiles || that_present_dotFiles) {
      if (!(this_present_dotFiles && that_present_dotFiles))
        return false;
      if (!this.dotFiles.equals(that.dotFiles))
        return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    List<Object> list = new ArrayList<Object>();

    boolean present_buildId = true && (isSetBuildId());
    list.add(present_buildId);
    if (present_buildId)
      list.add(buildId);

    boolean present_dotFiles = true && (isSetDotFiles());
    list.add(present_dotFiles);
    if (present_dotFiles)
      list.add(dotFiles);

    return list.hashCode();
  }

  @Override
  public int compareTo(SetBuckDotFilePathsRequest other) {
    if (!getClass().equals(other.getClass())) {
      return getClass().getName().compareTo(other.getClass().getName());
    }

    int lastComparison = 0;

    lastComparison = Boolean.valueOf(isSetBuildId()).compareTo(other.isSetBuildId());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetBuildId()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.buildId, other.buildId);
      if (lastComparison != 0) {
        return lastComparison;
      }
    }
    lastComparison = Boolean.valueOf(isSetDotFiles()).compareTo(other.isSetDotFiles());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetDotFiles()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.dotFiles, other.dotFiles);
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
    StringBuilder sb = new StringBuilder("SetBuckDotFilePathsRequest(");
    boolean first = true;

    if (isSetBuildId()) {
      sb.append("buildId:");
      if (this.buildId == null) {
        sb.append("null");
      } else {
        sb.append(this.buildId);
      }
      first = false;
    }
    if (isSetDotFiles()) {
      if (!first) sb.append(", ");
      sb.append("dotFiles:");
      if (this.dotFiles == null) {
        sb.append("null");
      } else {
        sb.append(this.dotFiles);
      }
      first = false;
    }
    sb.append(")");
    return sb.toString();
  }

  public void validate() throws org.apache.thrift.TException {
    // check for required fields
    // check for sub-struct validity
    if (buildId != null) {
      buildId.validate();
    }
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

  private static class SetBuckDotFilePathsRequestStandardSchemeFactory implements SchemeFactory {
    public SetBuckDotFilePathsRequestStandardScheme getScheme() {
      return new SetBuckDotFilePathsRequestStandardScheme();
    }
  }

  private static class SetBuckDotFilePathsRequestStandardScheme extends StandardScheme<SetBuckDotFilePathsRequest> {

    public void read(org.apache.thrift.protocol.TProtocol iprot, SetBuckDotFilePathsRequest struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TField schemeField;
      iprot.readStructBegin();
      while (true)
      {
        schemeField = iprot.readFieldBegin();
        if (schemeField.type == org.apache.thrift.protocol.TType.STOP) { 
          break;
        }
        switch (schemeField.id) {
          case 1: // BUILD_ID
            if (schemeField.type == org.apache.thrift.protocol.TType.STRUCT) {
              struct.buildId = new BuildId();
              struct.buildId.read(iprot);
              struct.setBuildIdIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          case 2: // DOT_FILES
            if (schemeField.type == org.apache.thrift.protocol.TType.LIST) {
              {
                org.apache.thrift.protocol.TList _list90 = iprot.readListBegin();
                struct.dotFiles = new ArrayList<PathInfo>(_list90.size);
                PathInfo _elem91;
                for (int _i92 = 0; _i92 < _list90.size; ++_i92)
                {
                  _elem91 = new PathInfo();
                  _elem91.read(iprot);
                  struct.dotFiles.add(_elem91);
                }
                iprot.readListEnd();
              }
              struct.setDotFilesIsSet(true);
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

    public void write(org.apache.thrift.protocol.TProtocol oprot, SetBuckDotFilePathsRequest struct) throws org.apache.thrift.TException {
      struct.validate();

      oprot.writeStructBegin(STRUCT_DESC);
      if (struct.buildId != null) {
        if (struct.isSetBuildId()) {
          oprot.writeFieldBegin(BUILD_ID_FIELD_DESC);
          struct.buildId.write(oprot);
          oprot.writeFieldEnd();
        }
      }
      if (struct.dotFiles != null) {
        if (struct.isSetDotFiles()) {
          oprot.writeFieldBegin(DOT_FILES_FIELD_DESC);
          {
            oprot.writeListBegin(new org.apache.thrift.protocol.TList(org.apache.thrift.protocol.TType.STRUCT, struct.dotFiles.size()));
            for (PathInfo _iter93 : struct.dotFiles)
            {
              _iter93.write(oprot);
            }
            oprot.writeListEnd();
          }
          oprot.writeFieldEnd();
        }
      }
      oprot.writeFieldStop();
      oprot.writeStructEnd();
    }

  }

  private static class SetBuckDotFilePathsRequestTupleSchemeFactory implements SchemeFactory {
    public SetBuckDotFilePathsRequestTupleScheme getScheme() {
      return new SetBuckDotFilePathsRequestTupleScheme();
    }
  }

  private static class SetBuckDotFilePathsRequestTupleScheme extends TupleScheme<SetBuckDotFilePathsRequest> {

    @Override
    public void write(org.apache.thrift.protocol.TProtocol prot, SetBuckDotFilePathsRequest struct) throws org.apache.thrift.TException {
      TTupleProtocol oprot = (TTupleProtocol) prot;
      BitSet optionals = new BitSet();
      if (struct.isSetBuildId()) {
        optionals.set(0);
      }
      if (struct.isSetDotFiles()) {
        optionals.set(1);
      }
      oprot.writeBitSet(optionals, 2);
      if (struct.isSetBuildId()) {
        struct.buildId.write(oprot);
      }
      if (struct.isSetDotFiles()) {
        {
          oprot.writeI32(struct.dotFiles.size());
          for (PathInfo _iter94 : struct.dotFiles)
          {
            _iter94.write(oprot);
          }
        }
      }
    }

    @Override
    public void read(org.apache.thrift.protocol.TProtocol prot, SetBuckDotFilePathsRequest struct) throws org.apache.thrift.TException {
      TTupleProtocol iprot = (TTupleProtocol) prot;
      BitSet incoming = iprot.readBitSet(2);
      if (incoming.get(0)) {
        struct.buildId = new BuildId();
        struct.buildId.read(iprot);
        struct.setBuildIdIsSet(true);
      }
      if (incoming.get(1)) {
        {
          org.apache.thrift.protocol.TList _list95 = new org.apache.thrift.protocol.TList(org.apache.thrift.protocol.TType.STRUCT, iprot.readI32());
          struct.dotFiles = new ArrayList<PathInfo>(_list95.size);
          PathInfo _elem96;
          for (int _i97 = 0; _i97 < _list95.size; ++_i97)
          {
            _elem96 = new PathInfo();
            _elem96.read(iprot);
            struct.dotFiles.add(_elem96);
          }
        }
        struct.setDotFilesIsSet(true);
      }
    }
  }

}

