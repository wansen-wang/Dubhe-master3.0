# -*- coding: utf-8 -*-
# Generated by the protocol buffer compiler.  DO NOT EDIT!
# source: oneflow/customized/utils/tensor.proto
"""Generated protocol buffer code."""
from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from google.protobuf import reflection as _reflection
from google.protobuf import symbol_database as _symbol_database
# @@protoc_insertion_point(imports)

_sym_db = _symbol_database.Default()




DESCRIPTOR = _descriptor.FileDescriptor(
  name='oneflow/customized/utils/tensor.proto',
  package='oneflow',
  syntax='proto3',
  serialized_options=None,
  create_key=_descriptor._internal_create_key,
  serialized_pb=b'\n%oneflow/customized/utils/tensor.proto\x12\x07oneflow\"w\n\x10TensorShapeProto\x12*\n\x03\x64im\x18\x02 \x03(\x0b\x32\x1d.oneflow.TensorShapeProto.Dim\x12\x14\n\x0cunknown_rank\x18\x03 \x01(\x08\x1a!\n\x03\x44im\x12\x0c\n\x04size\x18\x01 \x01(\x03\x12\x0c\n\x04name\x18\x02 \x01(\t\"\xcc\x02\n\x0bTensorProto\x12\r\n\x05\x64type\x18\x01 \x01(\t\x12/\n\x0ctensor_shape\x18\x02 \x01(\x0b\x32\x19.oneflow.TensorShapeProto\x12\x16\n\x0eversion_number\x18\x03 \x01(\x05\x12\x16\n\x0etensor_content\x18\x04 \x01(\x0c\x12\x14\n\x08half_val\x18\r \x03(\x05\x42\x02\x10\x01\x12\x15\n\tfloat_val\x18\x05 \x03(\x02\x42\x02\x10\x01\x12\x16\n\ndouble_val\x18\x06 \x03(\x01\x42\x02\x10\x01\x12\x13\n\x07int_val\x18\x07 \x03(\x05\x42\x02\x10\x01\x12\x12\n\nstring_val\x18\x08 \x03(\x0c\x12\x18\n\x0cscomplex_val\x18\t \x03(\x02\x42\x02\x10\x01\x12\x15\n\tint64_val\x18\n \x03(\x03\x42\x02\x10\x01\x12\x14\n\x08\x62ool_val\x18\x0b \x03(\x08\x42\x02\x10\x01\x12\x18\n\x0c\x64\x63omplex_val\x18\x0c \x03(\x01\x42\x02\x10\x01\x62\x06proto3'
)




_TENSORSHAPEPROTO_DIM = _descriptor.Descriptor(
  name='Dim',
  full_name='oneflow.TensorShapeProto.Dim',
  filename=None,
  file=DESCRIPTOR,
  containing_type=None,
  create_key=_descriptor._internal_create_key,
  fields=[
    _descriptor.FieldDescriptor(
      name='size', full_name='oneflow.TensorShapeProto.Dim.size', index=0,
      number=1, type=3, cpp_type=2, label=1,
      has_default_value=False, default_value=0,
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      serialized_options=None, file=DESCRIPTOR,  create_key=_descriptor._internal_create_key),
    _descriptor.FieldDescriptor(
      name='name', full_name='oneflow.TensorShapeProto.Dim.name', index=1,
      number=2, type=9, cpp_type=9, label=1,
      has_default_value=False, default_value=b"".decode('utf-8'),
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      serialized_options=None, file=DESCRIPTOR,  create_key=_descriptor._internal_create_key),
  ],
  extensions=[
  ],
  nested_types=[],
  enum_types=[
  ],
  serialized_options=None,
  is_extendable=False,
  syntax='proto3',
  extension_ranges=[],
  oneofs=[
  ],
  serialized_start=136,
  serialized_end=169,
)

_TENSORSHAPEPROTO = _descriptor.Descriptor(
  name='TensorShapeProto',
  full_name='oneflow.TensorShapeProto',
  filename=None,
  file=DESCRIPTOR,
  containing_type=None,
  create_key=_descriptor._internal_create_key,
  fields=[
    _descriptor.FieldDescriptor(
      name='dim', full_name='oneflow.TensorShapeProto.dim', index=0,
      number=2, type=11, cpp_type=10, label=3,
      has_default_value=False, default_value=[],
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      serialized_options=None, file=DESCRIPTOR,  create_key=_descriptor._internal_create_key),
    _descriptor.FieldDescriptor(
      name='unknown_rank', full_name='oneflow.TensorShapeProto.unknown_rank', index=1,
      number=3, type=8, cpp_type=7, label=1,
      has_default_value=False, default_value=False,
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      serialized_options=None, file=DESCRIPTOR,  create_key=_descriptor._internal_create_key),
  ],
  extensions=[
  ],
  nested_types=[_TENSORSHAPEPROTO_DIM, ],
  enum_types=[
  ],
  serialized_options=None,
  is_extendable=False,
  syntax='proto3',
  extension_ranges=[],
  oneofs=[
  ],
  serialized_start=50,
  serialized_end=169,
)


_TENSORPROTO = _descriptor.Descriptor(
  name='TensorProto',
  full_name='oneflow.TensorProto',
  filename=None,
  file=DESCRIPTOR,
  containing_type=None,
  create_key=_descriptor._internal_create_key,
  fields=[
    _descriptor.FieldDescriptor(
      name='dtype', full_name='oneflow.TensorProto.dtype', index=0,
      number=1, type=9, cpp_type=9, label=1,
      has_default_value=False, default_value=b"".decode('utf-8'),
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      serialized_options=None, file=DESCRIPTOR,  create_key=_descriptor._internal_create_key),
    _descriptor.FieldDescriptor(
      name='tensor_shape', full_name='oneflow.TensorProto.tensor_shape', index=1,
      number=2, type=11, cpp_type=10, label=1,
      has_default_value=False, default_value=None,
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      serialized_options=None, file=DESCRIPTOR,  create_key=_descriptor._internal_create_key),
    _descriptor.FieldDescriptor(
      name='version_number', full_name='oneflow.TensorProto.version_number', index=2,
      number=3, type=5, cpp_type=1, label=1,
      has_default_value=False, default_value=0,
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      serialized_options=None, file=DESCRIPTOR,  create_key=_descriptor._internal_create_key),
    _descriptor.FieldDescriptor(
      name='tensor_content', full_name='oneflow.TensorProto.tensor_content', index=3,
      number=4, type=12, cpp_type=9, label=1,
      has_default_value=False, default_value=b"",
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      serialized_options=None, file=DESCRIPTOR,  create_key=_descriptor._internal_create_key),
    _descriptor.FieldDescriptor(
      name='half_val', full_name='oneflow.TensorProto.half_val', index=4,
      number=13, type=5, cpp_type=1, label=3,
      has_default_value=False, default_value=[],
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      serialized_options=b'\020\001', file=DESCRIPTOR,  create_key=_descriptor._internal_create_key),
    _descriptor.FieldDescriptor(
      name='float_val', full_name='oneflow.TensorProto.float_val', index=5,
      number=5, type=2, cpp_type=6, label=3,
      has_default_value=False, default_value=[],
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      serialized_options=b'\020\001', file=DESCRIPTOR,  create_key=_descriptor._internal_create_key),
    _descriptor.FieldDescriptor(
      name='double_val', full_name='oneflow.TensorProto.double_val', index=6,
      number=6, type=1, cpp_type=5, label=3,
      has_default_value=False, default_value=[],
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      serialized_options=b'\020\001', file=DESCRIPTOR,  create_key=_descriptor._internal_create_key),
    _descriptor.FieldDescriptor(
      name='int_val', full_name='oneflow.TensorProto.int_val', index=7,
      number=7, type=5, cpp_type=1, label=3,
      has_default_value=False, default_value=[],
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      serialized_options=b'\020\001', file=DESCRIPTOR,  create_key=_descriptor._internal_create_key),
    _descriptor.FieldDescriptor(
      name='string_val', full_name='oneflow.TensorProto.string_val', index=8,
      number=8, type=12, cpp_type=9, label=3,
      has_default_value=False, default_value=[],
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      serialized_options=None, file=DESCRIPTOR,  create_key=_descriptor._internal_create_key),
    _descriptor.FieldDescriptor(
      name='scomplex_val', full_name='oneflow.TensorProto.scomplex_val', index=9,
      number=9, type=2, cpp_type=6, label=3,
      has_default_value=False, default_value=[],
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      serialized_options=b'\020\001', file=DESCRIPTOR,  create_key=_descriptor._internal_create_key),
    _descriptor.FieldDescriptor(
      name='int64_val', full_name='oneflow.TensorProto.int64_val', index=10,
      number=10, type=3, cpp_type=2, label=3,
      has_default_value=False, default_value=[],
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      serialized_options=b'\020\001', file=DESCRIPTOR,  create_key=_descriptor._internal_create_key),
    _descriptor.FieldDescriptor(
      name='bool_val', full_name='oneflow.TensorProto.bool_val', index=11,
      number=11, type=8, cpp_type=7, label=3,
      has_default_value=False, default_value=[],
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      serialized_options=b'\020\001', file=DESCRIPTOR,  create_key=_descriptor._internal_create_key),
    _descriptor.FieldDescriptor(
      name='dcomplex_val', full_name='oneflow.TensorProto.dcomplex_val', index=12,
      number=12, type=1, cpp_type=5, label=3,
      has_default_value=False, default_value=[],
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      serialized_options=b'\020\001', file=DESCRIPTOR,  create_key=_descriptor._internal_create_key),
  ],
  extensions=[
  ],
  nested_types=[],
  enum_types=[
  ],
  serialized_options=None,
  is_extendable=False,
  syntax='proto3',
  extension_ranges=[],
  oneofs=[
  ],
  serialized_start=172,
  serialized_end=504,
)

_TENSORSHAPEPROTO_DIM.containing_type = _TENSORSHAPEPROTO
_TENSORSHAPEPROTO.fields_by_name['dim'].message_type = _TENSORSHAPEPROTO_DIM
_TENSORPROTO.fields_by_name['tensor_shape'].message_type = _TENSORSHAPEPROTO
DESCRIPTOR.message_types_by_name['TensorShapeProto'] = _TENSORSHAPEPROTO
DESCRIPTOR.message_types_by_name['TensorProto'] = _TENSORPROTO
_sym_db.RegisterFileDescriptor(DESCRIPTOR)

TensorShapeProto = _reflection.GeneratedProtocolMessageType('TensorShapeProto', (_message.Message,), {

  'Dim' : _reflection.GeneratedProtocolMessageType('Dim', (_message.Message,), {
    'DESCRIPTOR' : _TENSORSHAPEPROTO_DIM,
    '__module__' : 'oneflow.customized.utils.tensor_pb2'
    # @@protoc_insertion_point(class_scope:oneflow.TensorShapeProto.Dim)
    })
  ,
  'DESCRIPTOR' : _TENSORSHAPEPROTO,
  '__module__' : 'oneflow.customized.utils.tensor_pb2'
  # @@protoc_insertion_point(class_scope:oneflow.TensorShapeProto)
  })
_sym_db.RegisterMessage(TensorShapeProto)
_sym_db.RegisterMessage(TensorShapeProto.Dim)

TensorProto = _reflection.GeneratedProtocolMessageType('TensorProto', (_message.Message,), {
  'DESCRIPTOR' : _TENSORPROTO,
  '__module__' : 'oneflow.customized.utils.tensor_pb2'
  # @@protoc_insertion_point(class_scope:oneflow.TensorProto)
  })
_sym_db.RegisterMessage(TensorProto)


_TENSORPROTO.fields_by_name['half_val']._options = None
_TENSORPROTO.fields_by_name['float_val']._options = None
_TENSORPROTO.fields_by_name['double_val']._options = None
_TENSORPROTO.fields_by_name['int_val']._options = None
_TENSORPROTO.fields_by_name['scomplex_val']._options = None
_TENSORPROTO.fields_by_name['int64_val']._options = None
_TENSORPROTO.fields_by_name['bool_val']._options = None
_TENSORPROTO.fields_by_name['dcomplex_val']._options = None
# @@protoc_insertion_point(module_scope)
