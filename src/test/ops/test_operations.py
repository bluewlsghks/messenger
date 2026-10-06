import importlib.util,pathlib,unittest
from unittest.mock import patch
ROOT=pathlib.Path(__file__).resolve().parents[3]
def load(name):
 spec=importlib.util.spec_from_file_location(name,ROOT/'scripts'/'ops'/(name+'.py'));m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m);return m
backup=load('mongo_backup');audit=load('security_audit')
class OperationsTests(unittest.TestCase):
 def test_system_databases_and_injected_names_are_rejected(self):
  for name in ['admin','local','config','a;b','a.*','../private','']:
   with self.assertRaises(ValueError):backup.database(name)
 def test_remote_backup_requires_explicit_flag(self):
  with self.assertRaises(ValueError):backup.validate_uri('mongodb://example.invalid/db',False)
  backup.validate_uri('mongodb://127.0.0.1/test',False)
 def test_dependency_audit_follows_pagination(self):
  with patch.object(audit,'request',side_effect=[{'results':[{'vulns':[{'id':'GHSA-first'}],'next_page_token':'next'}]}, {'results':[{'vulns':[{'id':'GHSA-second'}]}]}]) as send:
   found=audit.audit([{'name':'example:lib','version':'1'}]);self.assertEqual(len(found),2);self.assertEqual(send.call_args_list[1].args[0]['queries'][0]['page_token'],'next')
 def test_invalid_audit_response_is_not_a_clean_result(self):
  with patch.object(audit,'request',return_value={'results':[]}):
   with self.assertRaises(ValueError):audit.audit([{'name':'example:lib','version':'1'}])
if __name__=='__main__':unittest.main()
