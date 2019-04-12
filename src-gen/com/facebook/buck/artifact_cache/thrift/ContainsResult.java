/**
 * Autogenerated by Thrift Compiler (0.11.0)
 *
 * DO NOT EDIT UNLESS YOU ARE SURE THAT YOU KNOW WHAT YOU ARE DOING
 *  @generated
 */
package com.facebook.buck.artifact_cache.thrift;

@SuppressWarnings({"cast", "rawtypes", "serial", "unchecked", "unused"})
@javax.annotation.Generated(value = "Autogenerated by Thrift Compiler (0.11.0)")
public class ContainsResult implements org.apache.thrift.TBase<ContainsResult, ContainsResult._Fields>, java.io.Serializable, Cloneable, Comparable<ContainsResult> {
  private static final org.apache.thrift.protocol.TStruct STRUCT_DESC = new org.apache.thrift.protocol.TStruct("ContainsResult");

  private static final org.apache.thrift.protocol.TField RESULT_TYPE_FIELD_DESC = new org.apache.thrift.protocol.TField("resultType", org.apache.thrift.protocol.TType.I32, (short)1);
  private static final org.apache.thrift.protocol.TField DEBUG_INFO_FIELD_DESC = new org.apache.thrift.protocol.TField("debugInfo", org.apache.thrift.protocol.TType.STRUCT, (short)2);

  private static final org.apache.thrift.scheme.SchemeFactory STANDARD_SCHEME_FACTORY = new ContainsResultStandardSchemeFactory();
  private static final org.apache.thrift.scheme.SchemeFactory TUPLE_SCHEME_FACTORY = new ContainsResultTupleSchemeFactory();

  /**
   * 
   * @see ContainsResultType
   */
  public ContainsResultType resultType; // optional
  public ContainsDebugInfo debugInfo; // optional

  /** The set of fields this struct contains, along with convenience methods for finding and manipulating them. */
  public enum _Fields implements org.apache.thrift.TFieldIdEnum {
    /**
     * 
     * @see ContainsResultType
     */
    RESULT_TYPE((short)1, "resultType"),
    DEBUG_INFO((short)2, "debugInfo");

    private static final java.util.Map<java.lang.String, _Fields> byName = new java.util.HashMap<java.lang.String, _Fields>();

    static {
      for (_Fields field : java.util.EnumSet.allOf(_Fields.class)) {
        byName.put(field.getFieldName(), field);
      }
    }

    /**
     * Find the _Fields constant that matches fieldId, or null if its not found.
     */
    public static _Fields findByThriftId(int fieldId) {
      switch(fieldId) {
        case 1: // RESULT_TYPE
          return RESULT_TYPE;
        case 2: // DEBUG_INFO
          return DEBUG_INFO;
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
      if (fields == null) throw new java.lang.IllegalArgumentException("Field " + fieldId + " doesn't exist!");
      return fields;
    }

    /**
     * Find the _Fields constant that matches name, or null if its not found.
     */
    public static _Fields findByName(java.lang.String name) {
      return byName.get(name);
    }

    private final short _thriftId;
    private final java.lang.String _fieldName;

    _Fields(short thriftId, java.lang.String fieldName) {
      _thriftId = thriftId;
      _fieldName = fieldName;
    }

    public short getThriftFieldId() {
      return _thriftId;
    }

    public java.lang.String getFieldName() {
      return _fieldName;
    }
  }

  // isset id assignments
  private static final _Fields optionals[] = {_Fields.RESULT_TYPE,_Fields.DEBUG_INFO};
  public static final java.util.Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> metaDataMap;
  static {
    java.util.Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> tmpMap = new java.util.EnumMap<_Fields, org.apache.thrift.meta_data.FieldMetaData>(_Fields.class);
    tmpMap.put(_Fields.RESULT_TYPE, new org.apache.thrift.meta_data.FieldMetaData("resultType", org.apache.thrift.TFieldRequirementType.OPTIONAL, 
        new org.apache.thrift.meta_data.EnumMetaData(org.apache.thrift.protocol.TType.ENUM, ContainsResultType.class)));
    tmpMap.put(_Fields.DEBUG_INFO, new org.apache.thrift.meta_data.FieldMetaData("debugInfo", org.apache.thrift.TFieldRequirementType.OPTIONAL, 
        new org.apache.thrift.meta_data.StructMetaData(org.apache.thrift.protocol.TType.STRUCT, ContainsDebugInfo.class)));
    metaDataMap = java.util.Collections.unmodifiableMap(tmpMap);
    org.apache.thrift.meta_data.FieldMetaData.addStructMetaDataMap(ContainsResult.class, metaDataMap);
  }

  public ContainsResult() {
  }

  /**
   * Performs a deep copy on <i>other</i>.
   */
  public ContainsResult(ContainsResult other) {
    if (other.isSetResultType()) {
      this.resultType = other.resultType;
    }
    if (other.isSetDebugInfo()) {
      this.debugInfo = new ContainsDebugInfo(other.debugInfo);
    }
  }

  public ContainsResult deepCopy() {
    return new ContainsResult(this);
  }

  @Override
  public void clear() {
    this.resultType = null;
    this.debugInfo = null;
  }

  /**
   * 
   * @see ContainsResultType
   */
  public ContainsResultType getResultType() {
    return this.resultType;
  }

  /**
   * 
   * @see ContainsResultType
   */
  public ContainsResult setResultType(ContainsResultType resultType) {
    this.resultType = resultType;
    return this;
  }

  public void unsetResultType() {
    this.resultType = null;
  }

  /** Returns true if field resultType is set (has been assigned a value) and false otherwise */
  public boolean isSetResultType() {
    return this.resultType != null;
  }

  public void setResultTypeIsSet(boolean value) {
    if (!value) {
      this.resultType = null;
    }
  }

  public ContainsDebugInfo getDebugInfo() {
    return this.debugInfo;
  }

  public ContainsResult setDebugInfo(ContainsDebugInfo debugInfo) {
    this.debugInfo = debugInfo;
    return this;
  }

  public void unsetDebugInfo() {
    this.debugInfo = null;
  }

  /** Returns true if field debugInfo is set (has been assigned a value) and false otherwise */
  public boolean isSetDebugInfo() {
    return this.debugInfo != null;
  }

  public void setDebugInfoIsSet(boolean value) {
    if (!value) {
      this.debugInfo = null;
    }
  }

  public void setFieldValue(_Fields field, java.lang.Object value) {
    switch (field) {
    case RESULT_TYPE:
      if (value == null) {
        unsetResultType();
      } else {
        setResultType((ContainsResultType)value);
      }
      break;

    case DEBUG_INFO:
      if (value == null) {
        unsetDebugInfo();
      } else {
        setDebugInfo((ContainsDebugInfo)value);
      }
      break;

    }
  }

  public java.lang.Object getFieldValue(_Fields field) {
    switch (field) {
    case RESULT_TYPE:
      return getResultType();

    case DEBUG_INFO:
      return getDebugInfo();

    }
    throw new java.lang.IllegalStateException();
  }

  /** Returns true if field corresponding to fieldID is set (has been assigned a value) and false otherwise */
  public boolean isSet(_Fields field) {
    if (field == null) {
      throw new java.lang.IllegalArgumentException();
    }

    switch (field) {
    case RESULT_TYPE:
      return isSetResultType();
    case DEBUG_INFO:
      return isSetDebugInfo();
    }
    throw new java.lang.IllegalStateException();
  }

  @Override
  public boolean equals(java.lang.Object that) {
    if (that == null)
      return false;
    if (that instanceof ContainsResult)
      return this.equals((ContainsResult)that);
    return false;
  }

  public boolean equals(ContainsResult that) {
    if (that == null)
      return false;
    if (this == that)
      return true;

    boolean this_present_resultType = true && this.isSetResultType();
    boolean that_present_resultType = true && that.isSetResultType();
    if (this_present_resultType || that_present_resultType) {
      if (!(this_present_resultType && that_present_resultType))
        return false;
      if (!this.resultType.equals(that.resultType))
        return false;
    }

    boolean this_present_debugInfo = true && this.isSetDebugInfo();
    boolean that_present_debugInfo = true && that.isSetDebugInfo();
    if (this_present_debugInfo || that_present_debugInfo) {
      if (!(this_present_debugInfo && that_present_debugInfo))
        return false;
      if (!this.debugInfo.equals(that.debugInfo))
        return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    int hashCode = 1;

    hashCode = hashCode * 8191 + ((isSetResultType()) ? 131071 : 524287);
    if (isSetResultType())
      hashCode = hashCode * 8191 + resultType.getValue();

    hashCode = hashCode * 8191 + ((isSetDebugInfo()) ? 131071 : 524287);
    if (isSetDebugInfo())
      hashCode = hashCode * 8191 + debugInfo.hashCode();

    return hashCode;
  }

  @Override
  public int compareTo(ContainsResult other) {
    if (!getClass().equals(other.getClass())) {
      return getClass().getName().compareTo(other.getClass().getName());
    }

    int lastComparison = 0;

    lastComparison = java.lang.Boolean.valueOf(isSetResultType()).compareTo(other.isSetResultType());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetResultType()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.resultType, other.resultType);
      if (lastComparison != 0) {
        return lastComparison;
      }
    }
    lastComparison = java.lang.Boolean.valueOf(isSetDebugInfo()).compareTo(other.isSetDebugInfo());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetDebugInfo()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.debugInfo, other.debugInfo);
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
    scheme(iprot).read(iprot, this);
  }

  public void write(org.apache.thrift.protocol.TProtocol oprot) throws org.apache.thrift.TException {
    scheme(oprot).write(oprot, this);
  }

  @Override
  public java.lang.String toString() {
    java.lang.StringBuilder sb = new java.lang.StringBuilder("ContainsResult(");
    boolean first = true;

    if (isSetResultType()) {
      sb.append("resultType:");
      if (this.resultType == null) {
        sb.append("null");
      } else {
        sb.append(this.resultType);
      }
      first = false;
    }
    if (isSetDebugInfo()) {
      if (!first) sb.append(", ");
      sb.append("debugInfo:");
      if (this.debugInfo == null) {
        sb.append("null");
      } else {
        sb.append(this.debugInfo);
      }
      first = false;
    }
    sb.append(")");
    return sb.toString();
  }

  public void validate() throws org.apache.thrift.TException {
    // check for required fields
    // check for sub-struct validity
    if (debugInfo != null) {
      debugInfo.validate();
    }
  }

  private void writeObject(java.io.ObjectOutputStream out) throws java.io.IOException {
    try {
      write(new org.apache.thrift.protocol.TCompactProtocol(new org.apache.thrift.transport.TIOStreamTransport(out)));
    } catch (org.apache.thrift.TException te) {
      throw new java.io.IOException(te);
    }
  }

  private void readObject(java.io.ObjectInputStream in) throws java.io.IOException, java.lang.ClassNotFoundException {
    try {
      read(new org.apache.thrift.protocol.TCompactProtocol(new org.apache.thrift.transport.TIOStreamTransport(in)));
    } catch (org.apache.thrift.TException te) {
      throw new java.io.IOException(te);
    }
  }

  private static class ContainsResultStandardSchemeFactory implements org.apache.thrift.scheme.SchemeFactory {
    public ContainsResultStandardScheme getScheme() {
      return new ContainsResultStandardScheme();
    }
  }

  private static class ContainsResultStandardScheme extends org.apache.thrift.scheme.StandardScheme<ContainsResult> {

    public void read(org.apache.thrift.protocol.TProtocol iprot, ContainsResult struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TField schemeField;
      iprot.readStructBegin();
      while (true)
      {
        schemeField = iprot.readFieldBegin();
        if (schemeField.type == org.apache.thrift.protocol.TType.STOP) { 
          break;
        }
        switch (schemeField.id) {
          case 1: // RESULT_TYPE
            if (schemeField.type == org.apache.thrift.protocol.TType.I32) {
              struct.resultType = com.facebook.buck.artifact_cache.thrift.ContainsResultType.findByValue(iprot.readI32());
              struct.setResultTypeIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          case 2: // DEBUG_INFO
            if (schemeField.type == org.apache.thrift.protocol.TType.STRUCT) {
              struct.debugInfo = new ContainsDebugInfo();
              struct.debugInfo.read(iprot);
              struct.setDebugInfoIsSet(true);
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

    public void write(org.apache.thrift.protocol.TProtocol oprot, ContainsResult struct) throws org.apache.thrift.TException {
      struct.validate();

      oprot.writeStructBegin(STRUCT_DESC);
      if (struct.resultType != null) {
        if (struct.isSetResultType()) {
          oprot.writeFieldBegin(RESULT_TYPE_FIELD_DESC);
          oprot.writeI32(struct.resultType.getValue());
          oprot.writeFieldEnd();
        }
      }
      if (struct.debugInfo != null) {
        if (struct.isSetDebugInfo()) {
          oprot.writeFieldBegin(DEBUG_INFO_FIELD_DESC);
          struct.debugInfo.write(oprot);
          oprot.writeFieldEnd();
        }
      }
      oprot.writeFieldStop();
      oprot.writeStructEnd();
    }

  }

  private static class ContainsResultTupleSchemeFactory implements org.apache.thrift.scheme.SchemeFactory {
    public ContainsResultTupleScheme getScheme() {
      return new ContainsResultTupleScheme();
    }
  }

  private static class ContainsResultTupleScheme extends org.apache.thrift.scheme.TupleScheme<ContainsResult> {

    @Override
    public void write(org.apache.thrift.protocol.TProtocol prot, ContainsResult struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TTupleProtocol oprot = (org.apache.thrift.protocol.TTupleProtocol) prot;
      java.util.BitSet optionals = new java.util.BitSet();
      if (struct.isSetResultType()) {
        optionals.set(0);
      }
      if (struct.isSetDebugInfo()) {
        optionals.set(1);
      }
      oprot.writeBitSet(optionals, 2);
      if (struct.isSetResultType()) {
        oprot.writeI32(struct.resultType.getValue());
      }
      if (struct.isSetDebugInfo()) {
        struct.debugInfo.write(oprot);
      }
    }

    @Override
    public void read(org.apache.thrift.protocol.TProtocol prot, ContainsResult struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TTupleProtocol iprot = (org.apache.thrift.protocol.TTupleProtocol) prot;
      java.util.BitSet incoming = iprot.readBitSet(2);
      if (incoming.get(0)) {
        struct.resultType = com.facebook.buck.artifact_cache.thrift.ContainsResultType.findByValue(iprot.readI32());
        struct.setResultTypeIsSet(true);
      }
      if (incoming.get(1)) {
        struct.debugInfo = new ContainsDebugInfo();
        struct.debugInfo.read(iprot);
        struct.setDebugInfoIsSet(true);
      }
    }
  }

  private static <S extends org.apache.thrift.scheme.IScheme> S scheme(org.apache.thrift.protocol.TProtocol proto) {
    return (org.apache.thrift.scheme.StandardScheme.class.equals(proto.getScheme()) ? STANDARD_SCHEME_FACTORY : TUPLE_SCHEME_FACTORY).getScheme();
  }
}

