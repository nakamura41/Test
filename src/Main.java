import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;

import java.awt.event.*;

import java.io.FileNotFoundException;

import java.text.BreakIterator;

import java.util.Locale;
import java.util.ResourceBundle;
import java.util.MissingResourceException;

import java.util.ArrayList;

import net.sf.clipsrules.jni.*;

/* Implement FindFact which returns just a FactAddressValue or null */
/* TBD Add size method to PrimitiveValue */

/*

Notes:

This example creates just a single environment. If you create multiple environments,
call the destroy method when you no longer need the environment. This will free the
C data structures associated with the environment.

   clips = new Environment();
      .
      .
      .
   clips.destroy();

Calling the clear, reset, load, loadFacts, run, eval, build, assertString,
and makeInstance methods can trigger CLIPS garbage collection. If you need
to retain access to a PrimitiveValue returned by a prior eval, assertString,
or makeInstance call, retain it and then release it after the call is made.

   PrimitiveValue pv1 = clips.eval("(myFunction foo)");
   pv1.retain();
   PrimitiveValue pv2 = clips.eval("(myFunction bar)");
      .
      .
      .
   pv1.release();

*/

class AnimalDemo implements ActionListener
{
    private enum InterviewState
    {
        GREETING,
        INTERVIEW,
        CONCLUSION
    };

    JLabel displayLabel;
    JButton nextButton;
    JButton prevButton;
    JPanel choicesPanel;
    ButtonGroup choicesButtons;
    ResourceBundle animalResources;

    Environment clips;
    boolean isExecuting = false;
    Thread executionThread;

    String lastAnswer;
    String relationAsserted;
    ArrayList<String> variableAsserts;
    ArrayList<String> priorAnswers;

    InterviewState interviewState;

    AnimalDemo()
    {
        try
        {
            animalResources = ResourceBundle.getBundle("resources.AnimalResources",Locale.getDefault());
        }
        catch (MissingResourceException mre)
        {
            mre.printStackTrace();
            return;
        }

        /*================================*/
        /* Create a new JFrame container. */
        /*================================*/

        JFrame jfrm = new JFrame(animalResources.getString("AnimalDemo"));

        /*=============================*/
        /* Specify FlowLayout manager. */
        /*=============================*/

        jfrm.getContentPane().setLayout(new GridLayout(3,1));

        /*=================================*/
        /* Give the frame an initial size. */
        /*=================================*/

        jfrm.setSize(350,200);

        /*=============================================================*/
        /* Terminate the program when the user closes the application. */
        /*=============================================================*/

        jfrm.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        /*===========================*/
        /* Create the display panel. */
        /*===========================*/

        JPanel displayPanel = new JPanel();
        displayLabel = new JLabel();
        displayPanel.add(displayLabel);

        /*===========================*/
        /* Create the choices panel. */
        /*===========================*/

        choicesPanel = new JPanel();
        choicesButtons = new ButtonGroup();

        /*===========================*/
        /* Create the buttons panel. */
        /*===========================*/

        JPanel buttonPanel = new JPanel();

        prevButton = new JButton(animalResources.getString("Prev"));
        prevButton.setActionCommand("Prev");
        buttonPanel.add(prevButton);
        prevButton.addActionListener(this);

        nextButton = new JButton(animalResources.getString("Next"));
        nextButton.setActionCommand("Next");
        buttonPanel.add(nextButton);
        nextButton.addActionListener(this);

        /*=====================================*/
        /* Add the panels to the content pane. */
        /*=====================================*/

        jfrm.getContentPane().add(displayPanel);
        jfrm.getContentPane().add(choicesPanel);
        jfrm.getContentPane().add(buttonPanel);

        /*===================================*/
        /* Initialize the state information. */
        /*===================================*/

        variableAsserts = new ArrayList<String>();
        priorAnswers = new ArrayList<String>();

        /*==================================*/
        /* Load and run the animal program. */
        /*==================================*/

        clips = new Environment();

        try
        {
            clips.loadFromResource("/resources/bcengine.clp");
            clips.loadFromResource("/resources/animal.clp");

            try
            {
                clips.loadFromResource("/resources/animal_" +
                        Locale.getDefault().getLanguage() + ".clp");
            }
            catch (FileNotFoundException fnfe)
            {
                if (Locale.getDefault().getLanguage().equals("en"))
                { throw fnfe; }
                else
                { clips.loadFromResource("/resources/animal_en.clp"); }
            }

            processRules();
        }
        catch (Exception e)
        {
            e.printStackTrace();
            System.exit(1);
        }

        /*====================*/
        /* Display the frame. */
        /*====================*/

        jfrm.setVisible(true);
    }

    /*******************/
    /* handleResponse: */
    /*******************/
    private void handleResponse() throws Exception
    {
        /*===========================*/
        /* Get the current UI state. */
        /*===========================*/

        FactAddressValue fv = clips.findFact("UI-state");

        /*========================================*/
        /* Determine the Next/Prev button states. */
        /*========================================*/

        if (fv.getSlotValue("state").toString().equals("conclusion"))
        {
            interviewState = InterviewState.CONCLUSION;
            nextButton.setActionCommand("Restart");
            nextButton.setText(animalResources.getString("Restart"));
            prevButton.setVisible(true);
            choicesPanel.setVisible(false);
        }
        else if (fv.getSlotValue("state").toString().equals("greeting"))
        {
            interviewState = InterviewState.GREETING;
            nextButton.setActionCommand("Next");
            nextButton.setText(animalResources.getString("Next"));
            prevButton.setVisible(false);
            choicesPanel.setVisible(false);
        }
        else
        {
            interviewState = InterviewState.INTERVIEW;
            nextButton.setActionCommand("Next");
            nextButton.setText(animalResources.getString("Next"));
            prevButton.setVisible(true);
            choicesPanel.setVisible(true);
        }

        /*=====================*/
        /* Set up the choices. */
        /*=====================*/

        choicesPanel.removeAll();
        choicesButtons = new ButtonGroup();

        MultifieldValue damf = (MultifieldValue) fv.getSlotValue("display-answers");
        MultifieldValue vamf = (MultifieldValue) fv.getSlotValue("valid-answers");

        String selected = fv.getSlotValue("response").toString();
        JRadioButton firstButton = null;

        for (int i = 0; i < damf.size(); i++)
        {
            LexemeValue da = (LexemeValue) damf.get(i);
            LexemeValue va = (LexemeValue) vamf.get(i);
            JRadioButton rButton;
            String buttonName, buttonText, buttonAnswer;

            buttonName = da.getValue();
            buttonText = buttonName.substring(0,1).toUpperCase() + buttonName.substring(1);
            buttonAnswer = va.getValue();

            if (((lastAnswer != null) && buttonAnswer.equals(lastAnswer)) ||
                    ((lastAnswer == null) && buttonAnswer.equals(selected)))
            { rButton = new JRadioButton(buttonText,true); }
            else
            { rButton = new JRadioButton(buttonText,false); }

            rButton.setActionCommand(buttonAnswer);
            choicesPanel.add(rButton);
            choicesButtons.add(rButton);

            if (firstButton == null)
            { firstButton = rButton; }
        }

        if ((choicesButtons.getSelection() == null) && (firstButton != null))
        { choicesButtons.setSelected(firstButton.getModel(),true); }

        choicesPanel.repaint();

        /*====================================*/
        /* Set the label to the display text. */
        /*====================================*/

        relationAsserted = ((LexemeValue) fv.getSlotValue("relation-asserted")).getValue();

        /*====================================*/
        /* Set the label to the display text. */
        /*====================================*/

        String theText = ((StringValue) fv.getSlotValue("display")).getValue();

        wrapLabelText(displayLabel,theText);

        executionThread = null;

        isExecuting = false;
    }

    /*########################*/
    /* ActionListener Methods */
    /*########################*/

    /*******************/
    /* actionPerformed */
    /*******************/
    public void actionPerformed(
            ActionEvent ae)
    {
        try
        { onActionPerformed(ae); }
        catch (Exception e)
        { e.printStackTrace(); }
    }

    /*************/
    /* runAnimal */
    /*************/
    public void runAnimal()
    {
        Runnable runThread =
                new Runnable()
                {
                    public void run()
                    {
                        try
                        { clips.run(); }
                        catch (CLIPSException e)
                        { e.printStackTrace(); }

                        SwingUtilities.invokeLater(
                                new Runnable()
                                {
                                    public void run()
                                    {
                                        try
                                        { handleResponse(); }
                                        catch (Exception e)
                                        { e.printStackTrace(); }
                                    }
                                });
                    }
                };

        isExecuting = true;

        executionThread = new Thread(runThread);

        executionThread.start();
    }

    /****************/
    /* processRules */
    /****************/
    private void processRules() throws CLIPSException
    {
        clips.reset();

        for (String factString : variableAsserts)
        {
            String assertCommand = "(assert " + factString + ")";
            clips.eval(assertCommand);
        }

        runAnimal();
    }

    /********************/
    /* nextButtonAction */
    /********************/
    private void nextButtonAction() throws CLIPSException
    {
        String theString;
        String theAnswer;

        lastAnswer = null;
        switch (interviewState)
        {
            /* Handle Next button. */
            case GREETING:
            case INTERVIEW:
                theAnswer = choicesButtons.getSelection().getActionCommand();
                theString = "(variable (name " + relationAsserted + ") (value " +  theAnswer + "))";
                variableAsserts.add(theString);
                priorAnswers.add(theAnswer);
                break;

            /* Handle Restart button. */
            case CONCLUSION:
                variableAsserts.clear();
                priorAnswers.clear();
                break;
        }

        processRules();
    }

    /********************/
    /* prevButtonAction */
    /********************/
    private void prevButtonAction() throws CLIPSException
    {
        lastAnswer = priorAnswers.get(priorAnswers.size() - 1);

        variableAsserts.remove(variableAsserts.size() - 1);
        priorAnswers.remove(priorAnswers.size() - 1);

        processRules();
    }

    /*********************/
    /* onActionPerformed */
    /*********************/
    private void onActionPerformed(
            ActionEvent ae) throws Exception
    {
        if (isExecuting) return;

        if (ae.getActionCommand().equals("Next"))
        { nextButtonAction(); }
        else if (ae.getActionCommand().equals("Restart"))
        { nextButtonAction(); }
        else if (ae.getActionCommand().equals("Prev"))
        { prevButtonAction(); }
    }

    /*****************/
    /* wrapLabelText */
    /*****************/
    private void wrapLabelText(
            JLabel label,
            String text)
    {
        FontMetrics fm = label.getFontMetrics(label.getFont());
        Container container = label.getParent();
        int containerWidth = container.getWidth();
        int textWidth = SwingUtilities.computeStringWidth(fm,text);
        int desiredWidth;

        if (textWidth <= containerWidth)
        { desiredWidth = containerWidth; }
        else
        {
            int lines = (int) ((textWidth + containerWidth) / containerWidth);

            desiredWidth = (int) (textWidth / lines);
        }

        BreakIterator boundary = BreakIterator.getWordInstance();
        boundary.setText(text);

        StringBuffer trial = new StringBuffer();
        StringBuffer real = new StringBuffer("<html><center>");

        int start = boundary.first();
        for (int end = boundary.next(); end != BreakIterator.DONE;
             start = end, end = boundary.next())
        {
            String word = text.substring(start,end);
            trial.append(word);
            int trialWidth = SwingUtilities.computeStringWidth(fm,trial.toString());
            if (trialWidth > containerWidth)
            {
                trial = new StringBuffer(word);
                real.append("<br>");
                real.append(word);
            }
            else if (trialWidth > desiredWidth)
            {
                trial = new StringBuffer("");
                real.append(word);
                real.append("<br>");
            }
            else
            { real.append(word); }
        }

        real.append("</html>");

        label.setText(real.toString());
    }

    public static void main(String args[])
    {
        // Create the frame on the event dispatching thread.
        SwingUtilities.invokeLater(
                new Runnable()
                {
                    public void run() { new AnimalDemo(); }
                });
    }
}
