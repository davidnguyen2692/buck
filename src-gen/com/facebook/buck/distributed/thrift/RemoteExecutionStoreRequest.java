/**
 * Autogenerated by Thrift Compiler (0.10.0)
 *
 * DO NOT EDIT UNLESS YOU ARE SURE THAT YOU KNOW WHAT YOU ARE DOING
 *  @generated
 */
package com.facebook.buck.distributed.thrift;

@SuppressWarnings({"cast", "rawtypes", "serial", "unchecked", "unused"})
@javax.annotation.Generated(value = "Autogenerated by Thrift Compiler (0.10.0)")
public class RemoteExecutionStoreRequest implements org.apache.thrift.TBase<RemoteExecutionStoreRequest, RemoteExecutionStoreRequest._Fields>, java.io.Serializable, Cloneable, Comparable<RemoteExecutionStoreRequest> {
  private static final org.apache.thrift.protocol.TStruct STRUCT_DESC = new org.apache.thrift.protocol.TStruct("RemoteExecutionStoreRequest");

  private static final org.apache.thrift.protocol.TField DIGESTS_FIELD_DESC = new org.apache.thrift.protocol.TField("digests", org.apache.thrift.protocol.TType.LIST, (short)1);

  private static final org.apache.thrift.scheme.SchemeFactory STANDARD_SCHEME_FACTORY = new RemoteExecutionStoreRequestStandardSchemeFactory();
  private static final org.apache.thrift.scheme.SchemeFactory TUPLE_SCHEME_FACTORY = new RemoteExecutionStoreRequestTupleSchemeFactory();

  public java.util.List<DigestAndContent> digests; // optional

  /** The set of fields this struct contains, along with convenience methods for finding and manipulating them. */
  public enum _Fields implements org.apache.thrift.TFieldIdEnum {
    DIGESTS((short)1, "digests");

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
        case 1: // DIGESTS
          return DIGESTS;
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
  private static final _Fields optionals[] = {_Fields.DIGESTS};
  public static final java.util.Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> metaDataMap;
  static {
    java.util.Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> tmpMap = new java.util.EnumMap<_Fields, org.apache.thrift.meta_data.FieldMetaData>(_Fields.class);
    tmpMap.put(_Fields.DIGESTS, new org.apache.thrift.meta_data.FieldMetaData("digests", org.apache.thrift.TFieldRequirementType.OPTIONAL, 
        new org.apache.thrift.meta_data.ListMetaData(org.apache.thrift.protocol.TType.LIST, 
            new org.apache.thrift.meta_data.StructMetaData(org.apache.thrift.protocol.TType.STRUCT, DigestAndContent.class))));
    metaDataMap = java.util.Collections.unmodifiableMap(tmpMap);
    org.apache.thrift.meta_data.FieldMetaData.addStructMetaDataMap(RemoteExecutionStoreRequest.class, metaDataMap);
  }

  public RemoteExecutionStoreRequest() {
  }

  /**
   * Performs a deep copy on <i>other</i>.
   */
  public RemoteExecutionStoreRequest(RemoteExecutionStoreRequest other) {
    if (other.isSetDigests()) {
      java.util.List<DigestAndContent> __this__digests = new java.util.ArrayList<DigestAndContent>(other.digests.size());
      for (DigestAndContent other_element : other.digests) {
        __this__digests.add(new DigestAndContent(other_element));
      }
      this.digests = __this__digests;
    }
  }

  public RemoteExecutionStoreRequest deepCopy() {
    return new RemoteExecutionStoreRequest(this);
  }

  @Override
  public void clear() {
    this.digests = null;
  }

  public int getDigestsSize() {
    return (this.digests == null) ? 0 : this.digests.size();
  }

  public java.util.Iterator<DigestAndContent> getDigestsIterator() {
    return (this.digests == null) ? null : this.digests.iterator();
  }

  public void addToDigests(DigestAndContent elem) {
    if (this.digests == null) {
      this.digests = new java.util.ArrayList<DigestAndContent>();
    }
    this.digests.add(elem);
  }

  public java.util.List<DigestAndContent> getDigests() {
    return this.digests;
  }

  public RemoteExecutionStoreRequest setDigests(java.util.List<DigestAndContent> digests) {
    this.digests = digests;
    return this;
  }

  public void unsetDigests() {
    this.digests = null;
  }

  /** Returns true if field digests is set (has been assigned a value) and false otherwise */
  public boolean isSetDigests() {
    return this.digests != null;
  }

  public void setDigestsIsSet(boolean value) {
    if (!value) {
      this.digests = null;
    }
  }

  public void setFieldValue(_Fields field, java.lang.Object value) {
    switch (field) {
    case DIGESTS:
      if (value == null) {
        unsetDigests();
      } else {
        setDigests((java.util.List<DigestAndContent>)value);
      }
      break;

    }
  }

  public java.lang.Object getFieldValue(_Fields field) {
    switch (field) {
    case DIGESTS:
      return getDigests();

    }
    throw new java.lang.IllegalStateException();
  }

  /** Returns true if field corresponding to fieldID is set (has been assigned a value) and false otherwise */
  public boolean isSet(_Fields field) {
    if (field == null) {
      throw new java.lang.IllegalArgumentException();
    }

    switch (field) {
    case DIGESTS:
      return isSetDigests();
    }
    throw new java.lang.IllegalStateException();
  }

  @Override
  public boolean equals(java.lang.Object that) {
    if (that == null)
      return false;
    if (that instanceof RemoteExecutionStoreRequest)
      return this.equals((RemoteExecutionStoreRequest)that);
    return false;
  }

  public boolean equals(RemoteExecutionStoreRequest that) {
    if (that == null)
      return false;
    if (this == that)
      return true;

    boolean this_present_digests = true && this.isSetDigests();
    boolean that_present_digests = true && that.isSetDigests();
    if (this_present_digests || that_present_digests) {
      if (!(this_present_digests && that_present_digests))
        return false;
      if (!this.digests.equals(that.digests))
        return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    int hashCode = 1;

    hashCode = hashCode * 8191 + ((isSetDigests()) ? 131071 : 524287);
    if (isSetDigests())
      hashCode = hashCode * 8191 + digests.hashCode();

    return hashCode;
  }

  @Override
  public int compareTo(RemoteExecutionStoreRequest other) {
    if (!getClass().equals(other.getClass())) {
      return getClass().getName().compareTo(other.getClass().getName());
    }

    int lastComparison = 0;

    lastComparison = java.lang.Boolean.valueOf(isSetDigests()).compareTo(other.isSetDigests());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetDigests()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.digests, other.digests);
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
    java.lang.StringBuilder sb = new java.lang.StringBuilder("RemoteExecutionStoreRequest(");
    boolean first = true;

    if (isSetDigests()) {
      sb.append("digests:");
      if (this.digests == null) {
        sb.append("null");
      } else {
        sb.append(this.digests);
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

  private void readObject(java.io.ObjectInputStream in) throws java.io.IOException, java.lang.ClassNotFoundException {
    try {
      read(new org.apache.thrift.protocol.TCompactProtocol(new org.apache.thrift.transport.TIOStreamTransport(in)));
    } catch (org.apache.thrift.TException te) {
      throw new java.io.IOException(te);
    }
  }

  private static class RemoteExecutionStoreRequestStandardSchemeFactory implements org.apache.thrift.scheme.SchemeFactory {
    public RemoteExecutionStoreRequestStandardScheme getScheme() {
      return new RemoteExecutionStoreRequestStandardScheme();
    }
  }

  private static class RemoteExecutionStoreRequestStandardScheme extends org.apache.thrift.scheme.StandardScheme<RemoteExecutionStoreRequest> {

    public void read(org.apache.thrift.protocol.TProtocol iprot, RemoteExecutionStoreRequest struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TField schemeField;
      iprot.readStructBegin();
      while (true)
      {
        schemeField = iprot.readFieldBegin();
        if (schemeField.type == org.apache.thrift.protocol.TType.STOP) { 
          break;
        }
        switch (schemeField.id) {
          case 1: // DIGESTS
            if (schemeField.type == org.apache.thrift.protocol.TType.LIST) {
              {
                org.apache.thrift.protocol.TList _list224 = iprot.readListBegin();
                struct.digests = new java.util.ArrayList<DigestAndContent>(_list224.size);
                DigestAndContent _elem225;
                for (int _i226 = 0; _i226 < _list224.size; ++_i226)
                {
                  _elem225 = new DigestAndContent();
                  _elem225.read(iprot);
                  struct.digests.add(_elem225);
                }
                iprot.readListEnd();
              }
              struct.setDigestsIsSet(true);
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

    public void write(org.apache.thrift.protocol.TProtocol oprot, RemoteExecutionStoreRequest struct) throws org.apache.thrift.TException {
      struct.validate();

      oprot.writeStructBegin(STRUCT_DESC);
      if (struct.digests != null) {
        if (struct.isSetDigests()) {
          oprot.writeFieldBegin(DIGESTS_FIELD_DESC);
          {
            oprot.writeListBegin(new org.apache.thrift.protocol.TList(org.apache.thrift.protocol.TType.STRUCT, struct.digests.size()));
            for (DigestAndContent _iter227 : struct.digests)
            {
              _iter227.write(oprot);
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

  private static class RemoteExecutionStoreRequestTupleSchemeFactory implements org.apache.thrift.scheme.SchemeFactory {
    public RemoteExecutionStoreRequestTupleScheme getScheme() {
      return new RemoteExecutionStoreRequestTupleScheme();
    }
  }

  private static class RemoteExecutionStoreRequestTupleScheme extends org.apache.thrift.scheme.TupleScheme<RemoteExecutionStoreRequest> {

    @Override
    public void write(org.apache.thrift.protocol.TProtocol prot, RemoteExecutionStoreRequest struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TTupleProtocol oprot = (org.apache.thrift.protocol.TTupleProtocol) prot;
      java.util.BitSet optionals = new java.util.BitSet();
      if (struct.isSetDigests()) {
        optionals.set(0);
      }
      oprot.writeBitSet(optionals, 1);
      if (struct.isSetDigests()) {
        {
          oprot.writeI32(struct.digests.size());
          for (DigestAndContent _iter228 : struct.digests)
          {
            _iter228.write(oprot);
          }
        }
      }
    }

    @Override
    public void read(org.apache.thrift.protocol.TProtocol prot, RemoteExecutionStoreRequest struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TTupleProtocol iprot = (org.apache.thrift.protocol.TTupleProtocol) prot;
      java.util.BitSet incoming = iprot.readBitSet(1);
      if (incoming.get(0)) {
        {
          org.apache.thrift.protocol.TList _list229 = new org.apache.thrift.protocol.TList(org.apache.thrift.protocol.TType.STRUCT, iprot.readI32());
          struct.digests = new java.util.ArrayList<DigestAndContent>(_list229.size);
          DigestAndContent _elem230;
          for (int _i231 = 0; _i231 < _list229.size; ++_i231)
          {
            _elem230 = new DigestAndContent();
            _elem230.read(iprot);
            struct.digests.add(_elem230);
          }
        }
        struct.setDigestsIsSet(true);
      }
    }
  }

  private static <S extends org.apache.thrift.scheme.IScheme> S scheme(org.apache.thrift.protocol.TProtocol proto) {
    return (org.apache.thrift.scheme.StandardScheme.class.equals(proto.getScheme()) ? STANDARD_SCHEME_FACTORY : TUPLE_SCHEME_FACTORY).getScheme();
  }
}

