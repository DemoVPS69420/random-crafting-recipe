import json
from pathlib import Path
import tempfile
import unittest

from release_core import properties, project_loader, required_java, discover_projects


class ProjectDetectionTests(unittest.TestCase):
    def test_legacy_metadata_and_loader_detection(self):
        parent = Path(__file__).resolve().parent / '.test-tmp'
        parent.mkdir(exist_ok=True)
        with tempfile.TemporaryDirectory(dir=parent) as folder:
            workspace = Path(folder).resolve()
            assert workspace.is_relative_to(parent.resolve())
            for name, metadata, loader in [
                ('1.12.2-Forge', 'mcmod.info', 'forge'),
                ('1.21.1-Forge', 'META-INF/mods.toml', 'forge'),
                ('1.21.1-NeoForge', 'META-INF/neoforge.mods.toml', 'neoforge'),
                ('26.3-Fabric', 'fabric.mod.json', 'fabric'),
            ]:
                project = workspace / name
                (project / 'src/main/java/com/randomcraft').mkdir(parents=True)
                file = project / 'src/main/resources' / metadata
                file.parent.mkdir(parents=True)
                (project / 'build.gradle').write_text('// fixture')
                mc = name.rsplit('-', 1)[0]
                (project / 'gradle.properties').write_text('' if mc == '1.12.2' else f'minecraft_version={mc}\nmod_version=1.2.0\n')
                file.write_text(json.dumps([{'modid': 'randomcraft', 'version': '1.2.0', 'mcversion': mc}]) if mc == '1.12.2' else '{}')
                self.assertEqual(project_loader(project), loader)
                self.assertEqual(properties(project)['mod_version'], '1.2.0')
                self.assertEqual(properties(project)['minecraft_version'], mc)
            self.assertEqual(required_java(workspace / '1.12.2-Forge'), 17)
            self.assertEqual(required_java(workspace / '26.3-Fabric'), 25)
            import shutil
            shutil.copytree(workspace / '26.3-Fabric', workspace / 'Library-26.3-Fabric')
            shutil.copytree(workspace / '26.3-Fabric', workspace / 'RepoBranches')
            self.assertEqual(len(discover_projects(workspace)), 4)


if __name__ == '__main__':
    unittest.main()
