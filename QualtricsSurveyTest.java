package au.com.suncorp.insurance.commons.core.components.qualtricssurvey;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import javax.script.Bindings;
import javax.script.SimpleBindings;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import com.day.cq.wcm.api.Page;

import io.wcm.testing.mock.aem.junit.AemContext;

@RunWith (MockitoJUnitRunner.class)
public class QualtricsSurveyTest
{

    @Rule
    public final AemContext aemContext = new AemContext(aemContext ->
    {
        aemContext.load().json("/repos/qualtricsSurveyTestContent.json", "/content/test-suncorp");
    }, ResourceResolverType.JCR_MOCK);

    private Bindings bindings = new SimpleBindings();
    private Resource resource;
    private QualtricsSurveyViewHelper qualtrics;

    @Before
    public void init() throws Exception
    {
        qualtrics = new QualtricsSurveyViewHelper();
    }

    private void setUp(String path)
    {
        resource = aemContext.resourceResolver().getResource(path);
        aemContext.currentResource(resource);
        bindings.put("request", aemContext.request());
        bindings.put("resource", resource);
        bindings.put("currentPage", resource.adaptTo(Page.class));
        bindings.put("properties", resource.getValueMap());

    }

    @Test
    public void templateScopeTest() throws Exception
    {
        setUp("/content/test-suncorp/jcr:content/qualtrics");
        qualtrics.init(bindings);
        assertThat(qualtrics.isPageScoped(), is(false));
    }

    @Test
    public void pageScopeTest() throws Exception
    {
        setUp("/content/test-suncorp/bank-accounts/savings-accounts/jcr:content/par/canvas/pars/grid/col_1/par/content_container/cc-parsys/qualtrics");
        qualtrics.init(bindings);
        assertThat(qualtrics.isPageScoped(), is(true));
    }

    @Test
    public void rootSurveyEnabledTest() throws Exception
    {
        setUp("/content/test-suncorp/jcr:content/qualtrics");
        qualtrics.init(bindings);
        assertThat(qualtrics.isSurveyEnabled(), is(true));
    }

    @Test @Ignore
    public void childInheritanceCancellationPriorityTest() throws Exception
    {
        setUp("/content/test-suncorp/bank-accounts/savings-accounts/jcr:content/qualtrics");
        qualtrics.init(bindings);
        assertThat(qualtrics.isSurveyEnabled(), is(false));
    }

    @Test @Ignore
    public void inheritedSurveyEnabledTest() throws Exception
    {
        setUp("/content/test-suncorp/bank-accounts/savings-accounts/online-savings/jcr:content/qualtrics");
        qualtrics.init(bindings);
        assertThat(qualtrics.isSurveyEnabled(), is(true));
        assertThat(qualtrics.getSurveyId(), is("ZN_9tVbOKsqted3mQt"));
    }

    @Test
    public void pageScopedEmptySurveyIDTest() throws Exception
    {
        setUp("/content/test-suncorp/bank-accounts/savings-accounts/online-savings/jcr:content/par/canvas/pars/grid/col_1/par/content_container/cc-parsys/qualtrics");
        qualtrics.init(bindings);
        assertThat(qualtrics.isSurveyEnabled(), is(false));
    }

}
